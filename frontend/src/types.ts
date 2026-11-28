export interface RoleBrief {
  code: string
  name: string
}

export interface MeInfo {
  id: number
  username: string
  displayName?: string
  email?: string
  enabled: boolean
  authVersion: number
  roles: RoleBrief[]
  permissions: string[]
}

export interface UserInfo {
  id: number
  username: string
  displayName?: string
  email?: string
  enabled: boolean
  roles: RoleBrief[]
  createdAt: string
}

export interface RoleInfo {
  id: number
  code: string
  name: string
  description?: string
  protectedRole: boolean
  permissions: string[]
  createdAt: string
}

export interface PermissionInfo {
  id: number
  code: string
  description?: string
}

export interface LoginResult {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: MeInfo
}

export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  traceId: string
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}

/** 审计事件只读视图（不含令牌与正文）。 */
export interface AuditEventInfo {
  id: number
  actorId?: number
  action: string
  targetType?: string
  targetId?: string
  result: string
  traceId?: string
  metadata?: string
  createdAt: string
}

// ---------------------------------------------------------------- 科研业务

export type ProjectStatus = 'DRAFT' | 'PENDING' | 'ACTIVE' | 'CLOSING' | 'ARCHIVED' | 'REJECTED'
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'SUBMITTED' | 'DONE' | 'RETURNED' | 'CANCELED'
export type ReportStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'RETURNED'
export type Priority = 'LOW' | 'NORMAL' | 'HIGH'
export type ReviewDecision = 'APPROVE' | 'REJECT'

export interface LabInfo {
  id: number
  code: string
  name: string
}

export interface ProjectInfo {
  id: number
  labId: number
  leaderId: number
  name: string
  objective?: string
  status: ProjectStatus
  plannedStart?: string
  plannedEnd?: string
  closeNote?: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface ProgressInfo {
  total: number
  done: number
  percent: number
}

export interface ProjectMemberInfo {
  userId: number
  memberType: 'LEADER' | 'MEMBER'
  active: boolean
  joinedAt: string
}

export interface ProjectDetailInfo {
  project: ProjectInfo
  progress: ProgressInfo
  members: ProjectMemberInfo[]
}

export interface ProjectReviewInfo {
  id: number
  projectId: number
  reviewType: 'INIT' | 'CLOSE'
  reviewerId: number
  decision: ReviewDecision
  reason?: string
  reviewedAt: string
}

export interface ProjectPermissionInfo {
  leader: boolean
  labPi: boolean
}

export interface TaskInfo {
  id: number
  projectId: number
  assigneeId: number
  title: string
  description?: string
  status: TaskStatus
  priority: Priority
  dueAt?: string
  cancelReason?: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface TaskSubmissionInfo {
  id: number
  taskId: number
  authorId: number
  submissionNo: number
  content: string
  reviewStatus: 'SUBMITTED' | 'APPROVED' | 'RETURNED'
  reviewerId?: number
  reviewNote?: string
  reviewedAt?: string
  createdAt: string
}

export interface ReportInfo {
  id: number
  projectId: number
  authorId: number
  weekStart: string
  work?: string
  blockers?: string
  nextPlan?: string
  status: ReportStatus
  reviewerId?: number
  reviewNote?: string
  version: number
  createdAt: string
  updatedAt: string
  taskIds: number[]
}
