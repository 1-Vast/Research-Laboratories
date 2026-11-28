<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import { apiErrorMessage } from '../api/http'
import {
  createReport,
  deleteReport,
  getProjectPermissions,
  listProjectTasks,
  listReports,
  reviewReport,
  submitReport,
  updateReport,
} from '../api/research'
import { authState, hasPermission } from '../stores/auth'
import { formatDate, reportStatusLabel, statusTagClass } from '../utils/labels'
import type { ReportInfo, TaskInfo } from '../types'

const route = useRoute()
const projectId = route.query.projectId ? Number(route.query.projectId) : null

const reports = ref<ReportInfo[]>([])
const tasks = ref<TaskInfo[]>([])
const isLeader = ref(false)
const isPi = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(10)
const error = ref('')
const loading = ref(false)
const busy = ref(false)

const editing = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  weekStart: '',
  work: '',
  blockers: '',
  nextPlan: '',
  taskIds: [] as number[],
  version: 0,
})

const myId = computed(() => authState.user?.id ?? 0)
const canReview = computed(
  () => (isLeader.value || isPi.value) && hasPermission('report:review'),
)
const canCreate = computed(() => projectId !== null && hasPermission('report:create'))

async function load() {
  if (projectId === null) {
    loading.value = false
    return
  }
  loading.value = true
  error.value = ''
  try {
    const result = await listReports(projectId, { page: page.value, size: size.value })
    reports.value = result.items
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
    const [perms, taskPage] = await Promise.all([
      getProjectPermissions(projectId),
      listProjectTasks(projectId, { page: 1, size: 100 }),
    ])
    isLeader.value = perms.leader
    isPi.value = perms.labPi
    tasks.value = taskPage.items
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

function startCreate() {
  editingId.value = null
  form.weekStart = new Date().toISOString().slice(0, 10)
  form.work = ''
  form.blockers = ''
  form.nextPlan = ''
  form.taskIds = []
  form.version = 0
  editing.value = true
}

function startEdit(report: ReportInfo) {
  editingId.value = report.id
  form.weekStart = report.weekStart
  form.work = report.work ?? ''
  form.blockers = report.blockers ?? ''
  form.nextPlan = report.nextPlan ?? ''
  form.taskIds = [...report.taskIds]
  form.version = report.version
  editing.value = true
}

async function save() {
  if (projectId === null) return
  busy.value = true
  error.value = ''
  try {
    if (editingId.value === null) {
      await createReport(projectId, {
        weekStart: form.weekStart,
        work: form.work || undefined,
        blockers: form.blockers || undefined,
        nextPlan: form.nextPlan || undefined,
        taskIds: form.taskIds,
      })
    } else {
      await updateReport(editingId.value, {
        work: form.work || undefined,
        blockers: form.blockers || undefined,
        nextPlan: form.nextPlan || undefined,
        taskIds: form.taskIds,
        version: form.version,
      })
    }
    editing.value = false
    await load()
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    busy.value = false
  }
}

function submit(report: ReportInfo) {
  run(() => submitReport(report.id, report.version))
}

function approve(report: ReportInfo) {
  run(() => reviewReport(report.id, { decision: 'APPROVE', version: report.version }))
}

function sendBack(report: ReportInfo) {
  const note = window.prompt('填写退回意见（必填）')
  if (!note) return
  run(() => reviewReport(report.id, { decision: 'REJECT', note, version: report.version }))
}

function remove(report: ReportInfo) {
  if (!window.confirm('确认删除该周报草稿？')) return
  run(() => deleteReport(report.id))
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
        <h2 style="margin: 0">进度周报</h2>
        <button v-if="canCreate" type="button" class="primary" @click="startCreate">写周报</button>
      </div>

      <p v-if="projectId === null" class="notice">
        请从项目详情页进入周报页面（周报按项目归档）。
      </p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>

      <template v-if="projectId !== null">
        <div class="toolbar">
          <span class="muted">共 {{ total }} 份周报</span>
        </div>

        <table>
          <thead>
            <tr>
              <th>周起始</th>
              <th>状态</th>
              <th>本周工作</th>
              <th>阻塞项</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="report in reports" :key="report.id">
              <td>{{ formatDate(report.weekStart) }}</td>
              <td><span :class="statusTagClass(report.status)">{{ reportStatusLabel[report.status] }}</span></td>
              <td>{{ report.work || '—' }}</td>
              <td>{{ report.blockers || '—' }}</td>
              <td>
                <button
                  v-if="report.authorId === myId && ['DRAFT', 'RETURNED'].includes(report.status)"
                  type="button"
                  :disabled="busy"
                  @click="startEdit(report)"
                >
                  编辑
                </button>
                <button
                  v-if="report.authorId === myId && ['DRAFT', 'RETURNED'].includes(report.status)"
                  type="button"
                  class="primary"
                  :disabled="busy"
                  @click="submit(report)"
                >
                  提交
                </button>
                <button
                  v-if="report.authorId === myId && report.status === 'DRAFT'"
                  type="button"
                  :disabled="busy"
                  @click="remove(report)"
                >
                  删除
                </button>
                <template v-if="canReview && report.status === 'SUBMITTED'">
                  <button type="button" class="primary" :disabled="busy" @click="approve(report)">通过</button>
                  <button type="button" :disabled="busy" @click="sendBack(report)">退回</button>
                </template>
              </td>
            </tr>
            <tr v-if="reports.length === 0 && !loading">
              <td colspan="5" class="muted">还没有周报。每周同一个人在同一项目下只能写一份。</td>
            </tr>
          </tbody>
        </table>

        <div class="actions">
          <button type="button" :disabled="page <= 1 || loading" @click="page -= 1; load()">上一页</button>
          <button
            type="button"
            :disabled="page * size >= total || loading"
            @click="page += 1; load()"
          >
            下一页
          </button>
          <span class="muted">第 {{ page }} 页</span>
        </div>
      </template>
    </section>

    <section v-if="editing" class="card" style="margin-top: 18px">
      <h2>{{ editingId === null ? '新建周报' : '编辑周报' }}</h2>
      <p class="notice">周起始日会自动归一到所在周的周一；提交后正文只读，需要修改请让负责人退回。</p>
      <form @submit.prevent="save">
        <div class="grid-2">
          <div class="field">
            <label for="r-week">周内任意一天</label>
            <input id="r-week" v-model="form.weekStart" type="date" :disabled="editingId !== null" required />
          </div>
          <div class="field">
            <label>关联任务（可选）</label>
            <select v-model="form.taskIds" multiple size="4">
              <option v-for="task in tasks" :key="task.id" :value="task.id">{{ task.title }}</option>
            </select>
          </div>
        </div>
        <div class="field">
          <label for="r-work">本周工作</label>
          <input id="r-work" v-model="form.work" maxlength="4000" />
        </div>
        <div class="field">
          <label for="r-block">阻塞项</label>
          <input id="r-block" v-model="form.blockers" maxlength="2000" />
        </div>
        <div class="field">
          <label for="r-next">下周计划</label>
          <input id="r-next" v-model="form.nextPlan" maxlength="2000" />
        </div>
        <div class="actions">
          <button class="primary" type="submit" :disabled="busy">保存草稿</button>
          <button type="button" @click="editing = false">取消</button>
        </div>
      </form>
    </section>
  </AppLayout>
</template>
