<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import { apiErrorMessage } from '../api/http'
import {
  assignUserRoles,
  createUser,
  deleteUser,
  listRoles,
  listUsers,
  setUserEnabled,
} from '../api/admin'
import type { RoleInfo, UserInfo } from '../types'

const users = ref<UserInfo[]>([])
const roles = ref<RoleInfo[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)
const error = ref('')

const form = reactive({ username: '', password: '', displayName: '', email: '', roles: [] as string[] })
const creating = ref(false)

const editingId = ref<number | null>(null)
const editingRoles = ref<string[]>([])

async function loadUsers() {
  loading.value = true
  error.value = ''
  try {
    const result = await listUsers({ keyword: keyword.value || undefined, page: page.value, size: size.value })
    users.value = result.items
    total.value = result.total
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  try {
    const result = await listRoles({ page: 1, size: 100 })
    roles.value = result.items
  } catch (err) {
    error.value = apiErrorMessage(err)
  }
}

async function submitCreate() {
  error.value = ''
  creating.value = true
  try {
    await createUser({
      username: form.username.trim(),
      password: form.password,
      displayName: form.displayName || undefined,
      email: form.email || undefined,
      roles: form.roles.length > 0 ? form.roles : undefined,
    })
    form.username = ''
    form.password = ''
    form.displayName = ''
    form.email = ''
    form.roles = []
    await loadUsers()
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    creating.value = false
  }
}

async function removeUser(user: UserInfo) {
  if (!window.confirm(`确认删除用户「${user.username}」？删除为逻辑删除，历史数据保留。`)) {
    return
  }
  error.value = ''
  try {
    await deleteUser(user.id)
    await loadUsers()
  } catch (err) {
    error.value = apiErrorMessage(err)
  }
}

async function toggleEnabled(user: UserInfo) {
  error.value = ''
  try {
    await setUserEnabled(user.id, !user.enabled)
    await loadUsers()
  } catch (err) {
    error.value = apiErrorMessage(err)
  }
}

function startEditRoles(user: UserInfo) {
  editingId.value = user.id
  editingRoles.value = user.roles.map((role) => role.code)
}

async function saveRoles() {
  if (editingId.value === null) {
    return
  }
  error.value = ''
  try {
    await assignUserRoles(editingId.value, editingRoles.value)
    editingId.value = null
    await loadUsers()
  } catch (err) {
    error.value = apiErrorMessage(err)
  }
}

function search() {
  page.value = 1
  loadUsers()
}

function prevPage() {
  if (page.value > 1) {
    page.value -= 1
    loadUsers()
  }
}

function nextPage() {
  if (page.value * size.value < total.value) {
    page.value += 1
    loadUsers()
  }
}

onMounted(async () => {
  await Promise.all([loadUsers(), loadRoles()])
})
</script>

<template>
  <AppLayout>
    <section class="card">
      <h2>用户管理</h2>
      <p v-if="error" class="error" role="alert">{{ error }}</p>

      <div class="toolbar">
        <input v-model="keyword" placeholder="按用户名 / 昵称 / 邮箱搜索" @keyup.enter="search" />
        <button type="button" @click="search">搜索</button>
        <span class="muted">共 {{ total }} 个用户，第 {{ page }} 页</span>
      </div>

      <table>
        <thead>
          <tr>
            <th>用户名</th>
            <th>昵称</th>
            <th>邮箱</th>
            <th>角色</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.id">
            <td>{{ user.username }}</td>
            <td>{{ user.displayName || '—' }}</td>
            <td>{{ user.email || '—' }}</td>
            <td>
              <template v-if="editingId === user.id">
                <label v-for="role in roles" :key="role.id" style="display: block; font-size: 12px">
                  <input
                    v-model="editingRoles"
                    type="checkbox"
                    :value="role.code"
                    style="width: auto; margin-right: 6px"
                  />
                  {{ role.name }}
                </label>
              </template>
              <template v-else>
                <span v-for="role in user.roles" :key="role.code" class="tag">{{ role.name }}</span>
              </template>
            </td>
            <td>
              <span class="tag" :class="user.enabled ? 'ok' : 'off'">{{ user.enabled ? '启用' : '禁用' }}</span>
            </td>
            <td>
              <template v-if="editingId === user.id">
                <button type="button" class="primary" @click="saveRoles">保存角色</button>
                <button type="button" @click="editingId = null">取消</button>
              </template>
              <template v-else>
                <button type="button" @click="startEditRoles(user)">分配角色</button>
                <button type="button" @click="toggleEnabled(user)">
                  {{ user.enabled ? '禁用' : '启用' }}
                </button>
                <button type="button" @click="removeUser(user)">删除</button>
              </template>
            </td>
          </tr>
          <tr v-if="users.length === 0">
            <td colspan="6" class="muted">没有匹配的用户</td>
          </tr>
        </tbody>
      </table>

      <div class="actions">
        <button type="button" :disabled="page <= 1 || loading" @click="prevPage">上一页</button>
        <button type="button" :disabled="page * size >= total || loading" @click="nextPage">下一页</button>
        <span class="muted">每页 {{ size }} 条</span>
      </div>
    </section>

    <section class="card" style="margin-top: 18px">
      <h2>新建用户</h2>
      <form @submit.prevent="submitCreate">
        <div class="grid-2">
          <div class="field">
            <label for="u-username">用户名</label>
            <input id="u-username" v-model="form.username" required minlength="3" maxlength="32" />
          </div>
          <div class="field">
            <label for="u-password">初始密码（至少 8 位，含字母和数字）</label>
            <input id="u-password" v-model="form.password" type="text" required minlength="8" />
          </div>
          <div class="field">
            <label for="u-name">昵称</label>
            <input id="u-name" v-model="form.displayName" maxlength="80" />
          </div>
          <div class="field">
            <label for="u-email">邮箱</label>
            <input id="u-email" v-model="form.email" type="email" />
          </div>
        </div>
        <div class="field">
          <label>角色（不选则默认科研人员）</label>
          <div class="chips">
            <label v-for="role in roles" :key="role.id" class="tag" style="cursor: pointer">
              <input
                v-model="form.roles"
                type="checkbox"
                :value="role.code"
                style="width: auto; margin-right: 4px"
              />
              {{ role.name }}
            </label>
          </div>
        </div>
        <button class="primary" type="submit" :disabled="creating">
          {{ creating ? '创建中…' : '创建用户' }}
        </button>
      </form>
    </section>
  </AppLayout>
</template>
