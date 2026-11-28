<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import { apiErrorMessage } from '../api/http'
import {
  cancelTask,
  createTask,
  getProjectPermissions,
  listMyTasks,
  listProjectMembers,
  listProjectTasks,
  reviewTask,
  startTask,
  submitTask,
} from '../api/research'
import { authState, hasPermission } from '../stores/auth'
import { formatDate, priorityLabel, statusTagClass, taskStatusLabel } from '../utils/labels'
import type { ProjectMemberInfo, TaskInfo, TaskStatus } from '../types'

const route = useRoute()
const projectId = route.query.projectId ? Number(route.query.projectId) : null

const tasks = ref<TaskInfo[]>([])
const members = ref<ProjectMemberInfo[]>([])
const isLeader = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(10)
const status = ref<TaskStatus | ''>('')
const error = ref('')
const loading = ref(false)
const busy = ref(false)

const creating = ref(false)
const form = reactive({
  assigneeId: '' as string,
  title: '',
  description: '',
  priority: 'NORMAL',
  dueAt: '',
})

const myId = computed(() => authState.user?.id ?? 0)
const canCreate = computed(() => projectId !== null && isLeader.value && hasPermission('task:create'))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = { status: status.value === '' ? undefined : status.value, page: page.value, size: size.value }
    const result = projectId !== null ? await listProjectTasks(projectId, params) : await listMyTasks(params)
    tasks.value = result.items
    total.value = result.total
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

async function loadContext() {
  if (projectId === null) {
    return
  }
  try {
    const [memberList, perms] = await Promise.all([
      listProjectMembers(projectId),
      getProjectPermissions(projectId),
    ])
    members.value = memberList
    isLeader.value = perms.leader
  } catch (err) {
    error.value = apiErrorMessage(err)
  }
}

async function run(action: () => Promise<unknown>) {
  busy.value = true
  error.value = ''
  try {
    await action()
    await load()
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    busy.value = false
  }
}

function start(task: TaskInfo) {
  run(() => startTask(task.id, task.version))
}

function submit(task: TaskInfo) {
  const content = window.prompt('填写交付内容（必填）')
  if (!content) return
  run(() => submitTask(task.id, { content, version: task.version }))
}

function approve(task: TaskInfo) {
  run(() => reviewTask(task.id, { decision: 'APPROVE', version: task.version }))
}

function sendBack(task: TaskInfo) {
  const note = window.prompt('填写退回原因（必填）')
  if (!note) return
  run(() => reviewTask(task.id, { decision: 'REJECT', note, version: task.version }))
}

function cancel(task: TaskInfo) {
  const reason = window.prompt('填写取消原因（必填）')
  if (!reason) return
  run(() => cancelTask(task.id, { reason, version: task.version }))
}

async function submitCreate() {
  if (projectId === null) return
  const assigneeId = Number(form.assigneeId)
  if (!assigneeId) {
    error.value = '请选择受派人'
    return
  }
  busy.value = true
  error.value = ''
  try {
    await createTask(projectId, {
      assigneeId,
      title: form.title.trim(),
      description: form.description || undefined,
      priority: form.priority,
      dueAt: form.dueAt ? new Date(form.dueAt).toISOString() : undefined,
    })
    form.title = ''
    form.description = ''
    form.dueAt = ''
    creating.value = false
    await load()
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    busy.value = false
  }
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

onMounted(async () => {
  await loadContext()
  await load()
})
</script>

<template>
  <AppLayout>
    <section class="card">
      <div class="toolbar" style="justify-content: space-between">
        <h2 style="margin: 0">{{ projectId === null ? '我的任务' : `项目 ${projectId} 的任务` }}</h2>
        <button v-if="canCreate" type="button" class="primary" @click="creating = !creating">
          {{ creating ? '收起' : '分派任务' }}
        </button>
      </div>
      <p v-if="error" class="error" role="alert">{{ error }}</p>

      <div class="toolbar">
        <select v-model="status" @change="load">
          <option value="">全部状态</option>
          <option v-for="(label, key) in taskStatusLabel" :key="key" :value="key">{{ label }}</option>
        </select>
        <span class="muted">共 {{ total }} 条</span>
      </div>

      <table>
        <thead>
          <tr>
            <th>任务</th>
            <th>状态</th>
            <th>优先级</th>
            <th>受派人</th>
            <th>截止</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="task in tasks" :key="task.id">
            <td>
              {{ task.title }}
              <div v-if="task.cancelReason" class="muted" style="font-size: 12px">
                取消原因：{{ task.cancelReason }}
              </div>
            </td>
            <td><span :class="statusTagClass(task.status)">{{ taskStatusLabel[task.status] }}</span></td>
            <td>{{ priorityLabel[task.priority] }}</td>
            <td>{{ task.assigneeId }}</td>
            <td>{{ formatDate(task.dueAt) }}</td>
            <td>
              <button
                v-if="task.assigneeId === myId && ['TODO', 'RETURNED'].includes(task.status)"
                type="button"
                :disabled="busy"
                @click="start(task)"
              >
                开始
              </button>
              <button
                v-if="task.assigneeId === myId && task.status === 'IN_PROGRESS'"
                type="button"
                class="primary"
                :disabled="busy"
                @click="submit(task)"
              >
                提交交付
              </button>
              <template v-if="isLeader && task.status === 'SUBMITTED'">
                <button type="button" class="primary" :disabled="busy" @click="approve(task)">验收</button>
                <button type="button" :disabled="busy" @click="sendBack(task)">退回</button>
              </template>
              <button
                v-if="isLeader && ['TODO', 'IN_PROGRESS', 'RETURNED'].includes(task.status)"
                type="button"
                :disabled="busy"
                @click="cancel(task)"
              >
                取消
              </button>
            </td>
          </tr>
          <tr v-if="tasks.length === 0 && !loading">
            <td colspan="6" class="muted">
              {{ projectId === null ? '还没有指派给你的任务' : '该项目还没有任务' }}
            </td>
          </tr>
        </tbody>
      </table>

      <div class="actions">
        <button type="button" :disabled="page <= 1 || loading" @click="prevPage">上一页</button>
        <button type="button" :disabled="page * size >= total || loading" @click="nextPage">下一页</button>
        <span class="muted">第 {{ page }} 页</span>
      </div>
    </section>

    <section v-if="canCreate && creating" class="card" style="margin-top: 18px">
      <h2>分派新任务</h2>
      <p class="notice">受派人必须是项目成员；任务只能在「进行中」的项目里创建。</p>
      <form @submit.prevent="submitCreate">
        <div class="grid-2">
          <div class="field">
            <label for="t-assignee">受派人</label>
            <select id="t-assignee" v-model="form.assigneeId" required>
              <option value="" disabled>请选择项目成员</option>
              <option v-for="member in members" :key="member.userId" :value="String(member.userId)">
                用户 {{ member.userId }}{{ member.memberType === 'LEADER' ? '（负责人）' : '' }}
              </option>
            </select>
          </div>
          <div class="field">
            <label for="t-title">任务标题</label>
            <input id="t-title" v-model="form.title" required maxlength="150" />
          </div>
          <div class="field">
            <label for="t-priority">优先级</label>
            <select id="t-priority" v-model="form.priority">
              <option value="LOW">低</option>
              <option value="NORMAL">中</option>
              <option value="HIGH">高</option>
            </select>
          </div>
          <div class="field">
            <label for="t-due">截止时间</label>
            <input id="t-due" v-model="form.dueAt" type="datetime-local" />
          </div>
        </div>
        <div class="field">
          <label for="t-desc">任务描述</label>
          <input id="t-desc" v-model="form.description" maxlength="4000" />
        </div>
        <button class="primary" type="submit" :disabled="busy">创建任务</button>
      </form>
    </section>
  </AppLayout>
</template>
