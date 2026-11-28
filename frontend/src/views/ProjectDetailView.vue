<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import { apiErrorMessage } from '../api/http'
import {
  addProjectMember,
  getProject,
  getProjectPermissions,
  listProjectReviews,
  removeProjectMember,
  reopenProject,
  requestCloseProject,
  reviewProject,
  reviewProjectClose,
  submitProject,
} from '../api/research'
import { hasPermission } from '../stores/auth'
import { formatDate, formatDateTime, projectStatusLabel, statusTagClass } from '../utils/labels'
import type { ProjectDetailInfo, ProjectPermissionInfo, ProjectReviewInfo } from '../types'

const route = useRoute()
const projectId = Number(route.params.id)

const detail = ref<ProjectDetailInfo | null>(null)
const reviews = ref<ProjectReviewInfo[]>([])
const perms = ref<ProjectPermissionInfo>({ leader: false, labPi: false })
const error = ref('')
const busy = ref(false)
const loading = ref(true)

const memberForm = reactive({ userId: '' as string })

const project = computed(() => detail.value?.project)
const canManageMembers = computed(
  () => perms.value.leader && ['ACTIVE', 'CLOSING'].includes(project.value?.status ?? ''),
)

async function load() {
  loading.value = true
  error.value = ''
  try {
    detail.value = await getProject(projectId)
    perms.value = await getProjectPermissions(projectId)
    reviews.value = await listProjectReviews(projectId)
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

async function run(action: () => Promise<unknown>, confirmText?: string) {
  if (confirmText && !window.confirm(confirmText)) {
    return
  }
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

function submitForApproval() {
  if (!project.value) return
  run(() => submitProject(projectId, project.value!.version))
}

function approve() {
  if (!project.value) return
  run(() => reviewProject(projectId, { decision: 'APPROVE', version: project.value!.version }))
}

function reject() {
  if (!project.value) return
  const reason = window.prompt('请填写驳回理由（必填）')
  if (!reason) return
  run(() => reviewProject(projectId, { decision: 'REJECT', reason, version: project.value!.version }))
}

function reopen() {
  if (!project.value) return
  run(() => reopenProject(projectId, project.value!.version))
}

function requestClose() {
  if (!project.value) return
  const note = window.prompt('结项说明（可选）') ?? undefined
  run(() => requestCloseProject(projectId, { closeNote: note, version: project.value!.version }))
}

function approveClose() {
  if (!project.value) return
  run(() => reviewProjectClose(projectId, { decision: 'APPROVE', version: project.value!.version }))
}

function rejectClose() {
  if (!project.value) return
  const reason = window.prompt('请填写退回结项的理由（必填）')
  if (!reason) return
  run(() => reviewProjectClose(projectId, { decision: 'REJECT', reason, version: project.value!.version }))
}

function addMember() {
  const userId = Number(memberForm.userId)
  if (!userId) {
    error.value = '请填写用户 ID'
    return
  }
  run(async () => {
    await addProjectMember(projectId, { userId, memberType: 'MEMBER' })
    memberForm.userId = ''
  })
}

function removeMember(userId: number) {
  run(() => removeProjectMember(projectId, userId), '确认移除该成员？移除后其将无法查看该项目。')
}

onMounted(load)
</script>

<template>
  <AppLayout>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="loading" class="muted">加载中…</p>

    <div v-if="project" class="stack">
      <section class="card">
        <div class="toolbar" style="justify-content: space-between; margin-bottom: 8px">
          <div>
            <h2 style="margin: 0">{{ project.name }}</h2>
            <div class="muted" style="font-size: 13px">{{ project.objective || '暂无目标说明' }}</div>
          </div>
          <span :class="statusTagClass(project.status)">{{ projectStatusLabel[project.status] }}</span>
        </div>

        <p class="muted" style="font-size: 13px">
          计划周期：{{ formatDate(project.plannedStart) }} ~ {{ formatDate(project.plannedEnd) }}
          · 负责人 ID：{{ project.leaderId }} · 版本：v{{ project.version }}
        </p>

        <div class="progress"><span :style="{ width: `${detail?.progress.percent ?? 0}%` }"></span></div>
        <p class="muted" style="font-size: 13px">
          进度 {{ detail?.progress.percent ?? 0 }}%（已验收 {{ detail?.progress.done ?? 0 }} /
          非取消任务 {{ detail?.progress.total ?? 0 }}）
        </p>

        <div class="actions">
          <button
            v-if="perms.leader && project.status === 'DRAFT' && hasPermission('project:submit')"
            type="button"
            class="primary"
            :disabled="busy"
            @click="submitForApproval"
          >
            提交立项
          </button>
          <button
            v-if="perms.leader && project.status === 'REJECTED' && hasPermission('project:update')"
            type="button"
            class="primary"
            :disabled="busy"
            @click="reopen"
          >
            重新编辑
          </button>
          <button
            v-if="perms.labPi && project.status === 'PENDING' && hasPermission('project:approve')"
            type="button"
            class="primary"
            :disabled="busy"
            @click="approve"
          >
            审批通过
          </button>
          <button
            v-if="perms.labPi && project.status === 'PENDING' && hasPermission('project:approve')"
            type="button"
            :disabled="busy"
            @click="reject"
          >
            驳回
          </button>
          <button
            v-if="perms.leader && project.status === 'ACTIVE' && hasPermission('project:close-request')"
            type="button"
            :disabled="busy"
            @click="requestClose"
          >
            申请结项
          </button>
          <button
            v-if="perms.labPi && project.status === 'CLOSING' && hasPermission('project:close-review')"
            type="button"
            class="primary"
            :disabled="busy"
            @click="approveClose"
          >
            批准归档
          </button>
          <button
            v-if="perms.labPi && project.status === 'CLOSING' && hasPermission('project:close-review')"
            type="button"
            :disabled="busy"
            @click="rejectClose"
          >
            退回结项
          </button>
          <RouterLink :to="`/tasks?projectId=${projectId}`">任务看板</RouterLink>
          <RouterLink :to="`/reports?projectId=${projectId}`">周报</RouterLink>
        </div>
      </section>

      <section class="card">
        <h2>项目成员</h2>
        <table>
          <thead>
            <tr>
              <th>用户 ID</th>
              <th>项目角色</th>
              <th>加入时间</th>
              <th v-if="canManageMembers">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="member in detail?.members ?? []" :key="member.userId">
              <td>{{ member.userId }}</td>
              <td>
                <span class="tag">{{ member.memberType === 'LEADER' ? '负责人' : '成员' }}</span>
              </td>
              <td>{{ formatDateTime(member.joinedAt) }}</td>
              <td v-if="canManageMembers">
                <button
                  v-if="member.memberType !== 'LEADER'"
                  type="button"
                  :disabled="busy"
                  @click="removeMember(member.userId)"
                >
                  移除
                </button>
                <span v-else class="muted">不可移除</span>
              </td>
            </tr>
          </tbody>
        </table>

        <form v-if="canManageMembers" class="inline-form" style="margin-top: 12px" @submit.prevent="addMember">
          <div class="field">
            <label for="m-user">按用户 ID 添加成员</label>
            <input id="m-user" v-model="memberForm.userId" type="number" min="1" placeholder="例如 4" />
          </div>
          <button type="submit" :disabled="busy">添加</button>
          <span class="muted" style="font-size: 12px">成员维护仅在进行中 / 待结项的项目开放</span>
        </form>
        <p v-else class="muted" style="font-size: 13px">
          只有项目负责人可以维护成员，且项目需处于进行中或待结项状态。
        </p>
      </section>

      <section class="card">
        <h2>审批轨迹</h2>
        <ul v-if="reviews.length > 0" class="timeline">
          <li v-for="review in reviews" :key="review.id">
            <strong>{{ review.reviewType === 'INIT' ? '立项' : '结项' }}</strong>
            {{ review.decision === 'APPROVE' ? '通过' : '驳回' }}
            · 审批人 {{ review.reviewerId }} · {{ formatDateTime(review.reviewedAt) }}
            <div v-if="review.reason" class="muted">理由：{{ review.reason }}</div>
          </li>
        </ul>
        <p v-else class="muted">还没有审批记录。</p>
      </section>
    </div>
  </AppLayout>
</template>
