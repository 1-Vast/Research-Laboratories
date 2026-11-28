<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import { apiErrorMessage } from '../api/http'
import { createProject, listMyLabs, listProjects } from '../api/research'
import { hasPermission } from '../stores/auth'
import { formatDate, projectStatusLabel, statusTagClass } from '../utils/labels'
import type { LabInfo, ProjectInfo, ProjectStatus } from '../types'

const projects = ref<ProjectInfo[]>([])
const labs = ref<LabInfo[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const status = ref<ProjectStatus | ''>('')
const labId = ref<number | ''>('')
const loading = ref(false)
const error = ref('')

const creating = ref(false)
const submitting = ref(false)
const form = reactive({
  labId: '' as number | '',
  name: '',
  objective: '',
  plannedStart: '',
  plannedEnd: '',
})

const canCreate = hasPermission('project:create')

async function loadLabs() {
  try {
    labs.value = await listMyLabs()
    if (labs.value.length === 1 && form.labId === '') {
      form.labId = labs.value[0].id
    }
  } catch (err) {
    error.value = apiErrorMessage(err)
  }
}

async function loadProjects() {
  loading.value = true
  error.value = ''
  try {
    const result = await listProjects({
      labId: labId.value === '' ? undefined : labId.value,
      status: status.value === '' ? undefined : status.value,
      keyword: keyword.value || undefined,
      page: page.value,
      size: size.value,
    })
    projects.value = result.items
    total.value = result.total
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  loadProjects()
}

async function submitCreate() {
  if (form.labId === '') {
    error.value = '请选择实验室'
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await createProject({
      labId: form.labId,
      name: form.name.trim(),
      objective: form.objective || undefined,
      plannedStart: form.plannedStart || undefined,
      plannedEnd: form.plannedEnd || undefined,
    })
    form.name = ''
    form.objective = ''
    form.plannedStart = ''
    form.plannedEnd = ''
    creating.value = false
    page.value = 1
    await loadProjects()
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    submitting.value = false
  }
}

function prevPage() {
  if (page.value > 1) {
    page.value -= 1
    loadProjects()
  }
}

function nextPage() {
  if (page.value * size.value < total.value) {
    page.value += 1
    loadProjects()
  }
}

onMounted(async () => {
  await loadLabs()
  await loadProjects()
})
</script>

<template>
  <AppLayout>
    <section class="card">
      <div class="toolbar" style="justify-content: space-between">
        <h2 style="margin: 0">科研项目</h2>
        <button v-if="canCreate" type="button" class="primary" @click="creating = !creating">
          {{ creating ? '收起' : '新建项目' }}
        </button>
      </div>
      <p v-if="error" class="error" role="alert">{{ error }}</p>

      <div class="toolbar">
        <select v-model="labId" @change="search">
          <option value="">全部实验室</option>
          <option v-for="lab in labs" :key="lab.id" :value="lab.id">{{ lab.name }}</option>
        </select>
        <select v-model="status" @change="search">
          <option value="">全部状态</option>
          <option v-for="(label, key) in projectStatusLabel" :key="key" :value="key">{{ label }}</option>
        </select>
        <input v-model="keyword" placeholder="按项目名称搜索" @keyup.enter="search" />
        <button type="button" @click="search">搜索</button>
        <span class="muted">共 {{ total }} 个项目</span>
      </div>

      <table>
        <thead>
          <tr>
            <th>项目</th>
            <th>状态</th>
            <th>计划周期</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="project in projects" :key="project.id">
            <td>
              <RouterLink :to="`/projects/${project.id}`">{{ project.name }}</RouterLink>
              <div class="muted" style="font-size: 12px">{{ project.objective || '暂无目标说明' }}</div>
            </td>
            <td>
              <span :class="statusTagClass(project.status)">{{ projectStatusLabel[project.status] }}</span>
            </td>
            <td>{{ formatDate(project.plannedStart) }} ~ {{ formatDate(project.plannedEnd) }}</td>
            <td>{{ formatDate(project.updatedAt) }}</td>
            <td>
              <RouterLink :to="`/projects/${project.id}`">查看详情</RouterLink>
            </td>
          </tr>
          <tr v-if="projects.length === 0 && !loading">
            <td colspan="5" class="muted">
              没有可见的项目。项目只对「项目成员」和「实验室 PI」可见，这是后端的强制约束。
            </td>
          </tr>
        </tbody>
      </table>

      <div class="actions">
        <button type="button" :disabled="page <= 1 || loading" @click="prevPage">上一页</button>
        <button type="button" :disabled="page * size >= total || loading" @click="nextPage">下一页</button>
        <span class="muted">第 {{ page }} 页，每页 {{ size }} 条</span>
      </div>
    </section>

    <section v-if="canCreate && creating" class="card" style="margin-top: 18px">
      <h2>新建项目草稿</h2>
      <p class="notice">草稿创建后需要提交立项，由实验室 PI 审批通过才会进入「进行中」。</p>
      <form @submit.prevent="submitCreate">
        <div class="grid-2">
          <div class="field">
            <label for="p-lab">实验室</label>
            <select id="p-lab" v-model="form.labId" required>
              <option value="" disabled>请选择</option>
              <option v-for="lab in labs" :key="lab.id" :value="lab.id">{{ lab.name }}</option>
            </select>
          </div>
          <div class="field">
            <label for="p-name">项目名称</label>
            <input id="p-name" v-model="form.name" required maxlength="150" />
          </div>
          <div class="field">
            <label for="p-start">计划开始</label>
            <input id="p-start" v-model="form.plannedStart" type="date" />
          </div>
          <div class="field">
            <label for="p-end">计划结束</label>
            <input id="p-end" v-model="form.plannedEnd" type="date" />
          </div>
        </div>
        <div class="field">
          <label for="p-obj">项目目标</label>
          <input id="p-obj" v-model="form.objective" maxlength="2000" />
        </div>
        <button class="primary" type="submit" :disabled="submitting">
          {{ submitting ? '创建中…' : '创建草稿' }}
        </button>
      </form>
    </section>
  </AppLayout>
</template>
