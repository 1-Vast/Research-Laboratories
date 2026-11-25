<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import { apiErrorMessage } from '../api/http'
import { listAuditEvents } from '../api/admin'
import type { AuditEventInfo } from '../types'

const events = ref<AuditEventInfo[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(15)
const action = ref('')
const loading = ref(false)
const error = ref('')

const quickActions = ['auth.login.failed', 'security.access.denied', 'user.create', 'role.assignPermissions']

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await listAuditEvents({
      action: action.value || undefined,
      page: page.value,
      size: size.value,
    })
    events.value = result.items
    total.value = result.total
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

function filterBy(value: string) {
  action.value = value
  page.value = 1
  load()
}

function search() {
  page.value = 1
  load()
}

function prevPage() {
  if (page.value > 1) {
    page.value -= 1
    load()
  }
}

function nextPage() {
  if (page.value * size.value < total.value) {
    page.value += 1
    load()
  }
}

function formatTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function resultClass(result: string) {
  if (result === 'SUCCESS') return 'ok'
  if (result === 'DENIED') return 'off'
  return ''
}

onMounted(load)
</script>

<template>
  <AppLayout>
    <section class="card">
      <h2>审计日志</h2>
      <p class="muted" style="font-size: 13px">
        记录登录、用户/角色变更与越权访问等敏感操作。拒绝事件使用独立事务落库，业务失败不会带走审计痕迹；
        普通角色只能看到与本人相关的记录。
      </p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>

      <div class="toolbar">
        <input v-model="action" placeholder="按动作筛选，例如 security.access.denied" @keyup.enter="search" />
        <button type="button" @click="search">筛选</button>
        <button type="button" @click="filterBy('')">全部</button>
        <span class="muted">共 {{ total }} 条，第 {{ page }} 页</span>
      </div>
      <div class="chips" style="margin-bottom: 12px">
        <button v-for="item in quickActions" :key="item" type="button" class="tag" @click="filterBy(item)">
          {{ item }}
        </button>
      </div>

      <table>
        <thead>
          <tr>
            <th>时间</th>
            <th>操作者</th>
            <th>动作</th>
            <th>对象</th>
            <th>结果</th>
            <th>traceId</th>
            <th>元数据</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="event in events" :key="event.id">
            <td>{{ formatTime(event.createdAt) }}</td>
            <td>{{ event.actorId ?? '未认证' }}</td>
            <td>{{ event.action }}</td>
            <td>{{ event.targetType || '—' }}{{ event.targetId ? ` #${event.targetId}` : '' }}</td>
            <td>
              <span class="tag" :class="resultClass(event.result)">{{ event.result }}</span>
            </td>
            <td class="muted">{{ event.traceId || '—' }}</td>
            <td class="muted">{{ event.metadata || '—' }}</td>
          </tr>
          <tr v-if="events.length === 0">
            <td colspan="7" class="muted">暂无审计记录</td>
          </tr>
        </tbody>
      </table>

      <div class="actions">
        <button type="button" :disabled="page <= 1 || loading" @click="prevPage">上一页</button>
        <button type="button" :disabled="page * size >= total || loading" @click="nextPage">下一页</button>
        <span class="muted">每页 {{ size }} 条</span>
      </div>
    </section>
  </AppLayout>
</template>
