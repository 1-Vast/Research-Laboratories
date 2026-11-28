import http from './http'
import type {
  ApiResponse,
  LabInfo,
  PageResult,
  ProjectDetailInfo,
  ProjectInfo,
  ProjectMemberInfo,
  ProjectPermissionInfo,
  ProjectReviewInfo,
  ProjectStatus,
  ReportInfo,
  ReportStatus,
  ReviewDecision,
  TaskInfo,
  TaskStatus,
  TaskSubmissionInfo,
} from '../types'

// ---------------------------------------------------------------- 实验室

export async function listMyLabs(): Promise<LabInfo[]> {
  const { data } = await http.get<ApiResponse<LabInfo[]>>('/labs')
  return data.data
}

// ---------------------------------------------------------------- 项目

export async function listProjects(params: {
  labId?: number
  status?: ProjectStatus
  keyword?: string
  sort?: string
  page: number
  size: number
}): Promise<PageResult<ProjectInfo>> {
  const { data } = await http.get<ApiResponse<PageResult<ProjectInfo>>>('/projects', { params })
  return data.data
}

export async function getProject(id: number): Promise<ProjectDetailInfo> {
  const { data } = await http.get<ApiResponse<ProjectDetailInfo>>(`/projects/${id}`)
  return data.data
}

export async function createProject(payload: {
  labId: number
  name: string
  objective?: string
  plannedStart?: string
  plannedEnd?: string
}): Promise<ProjectInfo> {
  const { data } = await http.post<ApiResponse<ProjectInfo>>('/projects', payload)
  return data.data
}

export async function updateProject(
  id: number,
  payload: {
    name: string
    objective?: string
    plannedStart?: string
    plannedEnd?: string
    version: number
  },
): Promise<ProjectInfo> {
  const { data } = await http.put<ApiResponse<ProjectInfo>>(`/projects/${id}`, payload)
  return data.data
}

export async function deleteProject(id: number): Promise<void> {
  await http.delete(`/projects/${id}`)
}

export async function submitProject(id: number, version: number): Promise<ProjectInfo> {
  const { data } = await http.post<ApiResponse<ProjectInfo>>(`/projects/${id}/submit`, { version })
  return data.data
}

export async function reviewProject(
  id: number,
  payload: { decision: ReviewDecision; reason?: string; version: number },
): Promise<ProjectInfo> {
  const action = payload.decision === 'APPROVE' ? 'approve' : 'reject'
  const { data } = await http.post<ApiResponse<ProjectInfo>>(`/projects/${id}/${action}`, payload)
  return data.data
}

export async function reopenProject(id: number, version: number): Promise<ProjectInfo> {
  const { data } = await http.post<ApiResponse<ProjectInfo>>(`/projects/${id}/reopen`, { version })
  return data.data
}

export async function requestCloseProject(
  id: number,
  payload: { closeNote?: string; version: number },
): Promise<ProjectInfo> {
  const { data } = await http.post<ApiResponse<ProjectInfo>>(`/projects/${id}/close-request`, payload)
  return data.data
}

export async function reviewProjectClose(
  id: number,
  payload: { decision: ReviewDecision; reason?: string; version: number },
): Promise<ProjectInfo> {
  const action = payload.decision === 'APPROVE' ? 'close-approve' : 'close-reject'
  const { data } = await http.post<ApiResponse<ProjectInfo>>(`/projects/${id}/${action}`, payload)
  return data.data
}

export async function listProjectMembers(id: number): Promise<ProjectMemberInfo[]> {
  const { data } = await http.get<ApiResponse<ProjectMemberInfo[]>>(`/projects/${id}/members`)
  return data.data
}

export async function addProjectMember(
  id: number,
  payload: { userId: number; memberType?: 'LEADER' | 'MEMBER' },
): Promise<ProjectMemberInfo> {
  const { data } = await http.post<ApiResponse<ProjectMemberInfo>>(`/projects/${id}/members`, payload)
  return data.data
}

export async function removeProjectMember(id: number, userId: number): Promise<void> {
  await http.delete(`/projects/${id}/members/${userId}`)
}

export async function listProjectReviews(id: number): Promise<ProjectReviewInfo[]> {
  const { data } = await http.get<ApiResponse<ProjectReviewInfo[]>>(`/projects/${id}/reviews`)
  return data.data
}

export async function getProjectPermissions(id: number): Promise<ProjectPermissionInfo> {
  const { data } = await http.get<ApiResponse<ProjectPermissionInfo>>(`/projects/${id}/permissions`)
  return data.data
}

// ---------------------------------------------------------------- 任务

export async function listProjectTasks(
  projectId: number,
  params: { status?: TaskStatus; assigneeId?: number; keyword?: string; page: number; size: number },
): Promise<PageResult<TaskInfo>> {
  const { data } = await http.get<ApiResponse<PageResult<TaskInfo>>>(`/projects/${projectId}/tasks`, { params })
  return data.data
}

export async function listMyTasks(params: {
  status?: TaskStatus
  page: number
  size: number
}): Promise<PageResult<TaskInfo>> {
  const { data } = await http.get<ApiResponse<PageResult<TaskInfo>>>('/tasks/mine', { params })
  return data.data
}

export async function createTask(
  projectId: number,
  payload: { assigneeId: number; title: string; description?: string; priority?: string; dueAt?: string },
): Promise<TaskInfo> {
  const { data } = await http.post<ApiResponse<TaskInfo>>(`/projects/${projectId}/tasks`, payload)
  return data.data
}

export async function startTask(id: number, version: number): Promise<TaskInfo> {
  const { data } = await http.post<ApiResponse<TaskInfo>>(`/tasks/${id}/start`, { version })
  return data.data
}

export async function submitTask(id: number, payload: { content: string; version: number }): Promise<TaskInfo> {
  const { data } = await http.post<ApiResponse<TaskInfo>>(`/tasks/${id}/submit`, payload)
  return data.data
}

export async function reviewTask(
  id: number,
  payload: { decision: ReviewDecision; note?: string; version: number },
): Promise<TaskInfo> {
  const { data } = await http.post<ApiResponse<TaskInfo>>(`/tasks/${id}/review`, payload)
  return data.data
}

export async function cancelTask(id: number, payload: { reason: string; version: number }): Promise<TaskInfo> {
  const { data } = await http.post<ApiResponse<TaskInfo>>(`/tasks/${id}/cancel`, payload)
  return data.data
}

export async function listTaskSubmissions(id: number): Promise<TaskSubmissionInfo[]> {
  const { data } = await http.get<ApiResponse<TaskSubmissionInfo[]>>(`/tasks/${id}/submissions`)
  return data.data
}

// ---------------------------------------------------------------- 周报

export async function listReports(
  projectId: number,
  params: { authorId?: number; status?: ReportStatus; page: number; size: number },
): Promise<PageResult<ReportInfo>> {
  const { data } = await http.get<ApiResponse<PageResult<ReportInfo>>>(`/projects/${projectId}/reports`, {
    params,
  })
  return data.data
}

export async function getReport(id: number): Promise<ReportInfo> {
  const { data } = await http.get<ApiResponse<ReportInfo>>(`/reports/${id}`)
  return data.data
}

export async function createReport(
  projectId: number,
  payload: {
    weekStart: string
    work?: string
    blockers?: string
    nextPlan?: string
    taskIds?: number[]
  },
): Promise<ReportInfo> {
  const { data } = await http.post<ApiResponse<ReportInfo>>(`/projects/${projectId}/reports`, payload)
  return data.data
}

export async function updateReport(
  id: number,
  payload: { work?: string; blockers?: string; nextPlan?: string; taskIds?: number[]; version: number },
): Promise<ReportInfo> {
  const { data } = await http.put<ApiResponse<ReportInfo>>(`/reports/${id}`, payload)
  return data.data
}

export async function submitReport(id: number, version: number): Promise<ReportInfo> {
  const { data } = await http.post<ApiResponse<ReportInfo>>(`/reports/${id}/submit`, { version })
  return data.data
}

export async function reviewReport(
  id: number,
  payload: { decision: ReviewDecision; note?: string; version: number },
): Promise<ReportInfo> {
  const { data } = await http.post<ApiResponse<ReportInfo>>(`/reports/${id}/review`, payload)
  return data.data
}

export async function deleteReport(id: number): Promise<void> {
  await http.delete(`/reports/${id}`)
}
