<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import { apiErrorMessage } from '../api/http'
import { assignRolePermissions, createRole, listPermissions, listRoles } from '../api/admin'
import type { PermissionInfo, RoleInfo } from '../types'

const roles = ref<RoleInfo[]>([])
const permissions = ref<PermissionInfo[]>([])
const selected = ref<RoleInfo | null>(null)
const draft = ref<string[]>([])
const error = ref('')
const message = ref('')
const saving = ref(false)

const form = ref({ code: '', name: '', description: '' })

const grouped = computed(() => {
  const groups = new Map<string, PermissionInfo[]>()
  for (const permission of permissions.value) {
    const prefix = permission.code.includes(':') ? permission.code.split(':')[0] : 'other'
    const list = groups.get(prefix) ?? []
    list.push(permission)
    groups.set(prefix, list)
  }
  return Array.from(groups.entries()).sort((a, b) => a[0].localeCompare(b[0]))
})

async function load() {
  error.value = ''
  try {
    const [roleResult, permissionList] = await Promise.all([
      listRoles({ page: 1, size: 100 }),
      listPermissions(),
    ])
    roles.value = roleResult.items
    permissions.value = permissionList
    if (!selected.value && roles.value.length > 0) {
      select(roles.value[0])
    }
  } catch (err) {
    error.value = apiErrorMessage(err)
  }
}

function select(role: RoleInfo) {
  selected.value = role
  draft.value = [...role.permissions]
  message.value = ''
}

async function savePermissions() {
  if (!selected.value) {
    return
  }
  error.value = ''
  message.value = ''
  saving.value = true
  try {
    const updated = await assignRolePermissions(selected.value.id, draft.value)
    selected.value = updated
    message.value = `已更新「${updated.name}」的权限，该角色用户下一次请求即按新权限生效`
    await load()
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    saving.value = false
  }
}

async function submitCreate() {
  error.value = ''
  message.value = ''
  try {
    const role = await createRole({
      code: form.value.code.trim(),
      name: form.value.name.trim(),
      description: form.value.description || undefined,
    })
    form.value = { code: '', name: '', description: '' }
    await load()
    select(role)
    message.value = '角色已创建，请为其勾选权限'
  } catch (err) {
    error.value = apiErrorMessage(err)
  }
}

onMounted(load)
</script>

<template>
  <AppLayout>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="message" class="notice">{{ message }}</p>

    <div class="grid-2">
      <section class="card">
        <h2>角色列表</h2>
        <table>
          <thead>
            <tr>
              <th>编码</th>
              <th>名称</th>
              <th>权限数</th>
              <th>类型</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="role in roles"
              :key="role.id"
              :style="{ background: selected?.id === role.id ? '#eef4fa' : '' }"
              style="cursor: pointer"
              @click="select(role)"
            >
              <td>{{ role.code }}</td>
              <td>{{ role.name }}</td>
              <td>{{ role.permissions.length }}</td>
              <td>
                <span class="tag" :class="role.protectedRole ? 'ok' : ''">
                  {{ role.protectedRole ? '内置' : '自定义' }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="card">
        <h2>新建角色</h2>
        <form @submit.prevent="submitCreate">
          <div class="field">
            <label for="role-code">角色编码（大写字母开头）</label>
            <input id="role-code" v-model="form.code" required maxlength="64" placeholder="例如 REVIEWER" />
          </div>
          <div class="field">
            <label for="role-name">角色名称</label>
            <input id="role-name" v-model="form.name" required maxlength="80" />
          </div>
          <div class="field">
            <label for="role-desc">描述</label>
            <input id="role-desc" v-model="form.description" maxlength="255" />
          </div>
          <button class="primary" type="submit">创建角色</button>
        </form>
      </section>
    </div>

    <section v-if="selected" class="card" style="margin-top: 18px">
      <h2>「{{ selected.name }}」的权限（已选 {{ draft.length }} 项）</h2>
      <div class="perm-grid">
        <template v-for="[group, list] in grouped" :key="group">
          <label v-for="permission in list" :key="permission.code" :title="permission.description">
            <input v-model="draft" type="checkbox" :value="permission.code" />
            {{ permission.code }}
          </label>
        </template>
      </div>
      <div class="actions">
        <button class="primary" type="button" :disabled="saving" @click="savePermissions">
          {{ saving ? '保存中…' : '保存权限' }}
        </button>
        <button type="button" @click="select(selected)">重置</button>
        <span v-if="selected.protectedRole" class="muted">
          内置管理员角色必须保留 user:manage 与 role:manage
        </span>
      </div>
    </section>
  </AppLayout>
</template>
