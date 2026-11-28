import type { Priority, ProjectStatus, ReportStatus, TaskStatus } from '../types'

/** 状态文案与配色集中在这里，避免每个页面各写一套导致说法不一致。 */
export const projectStatusLabel: Record<ProjectStatus, string> = {
  DRAFT: '草稿',
  PENDING: '待审批',
  ACTIVE: '进行中',
  CLOSING: '待结项',
  ARCHIVED: '已归档',
  REJECTED: '已驳回',
}

export const taskStatusLabel: Record<TaskStatus, string> = {
  TODO: '未开始',
  IN_PROGRESS: '进行中',
  SUBMITTED: '待验收',
  DONE: '已验收',
  RETURNED: '已退回',
  CANCELED: '已取消',
}

export const reportStatusLabel: Record<ReportStatus, string> = {
  DRAFT: '草稿',
  SUBMITTED: '待审核',
  APPROVED: '已通过',
  RETURNED: '已退回',
}

export const priorityLabel: Record<Priority, string> = {
  LOW: '低',
  NORMAL: '中',
  HIGH: '高',
}

/** 与 style.css 里的 .tag 变体对应：ok 绿、off 灰、warn 黄。 */
export function statusTagClass(status: string): string {
  if (['ACTIVE', 'DONE', 'APPROVED'].includes(status)) {
    return 'tag ok'
  }
  if (['DRAFT', 'CANCELED', 'ARCHIVED'].includes(status)) {
    return 'tag off'
  }
  if (['REJECTED', 'RETURNED'].includes(status)) {
    return 'tag warn'
  }
  return 'tag'
}

export function formatDate(value?: string): string {
  return value ? value.slice(0, 10) : '—'
}

export function formatDateTime(value?: string): string {
  if (!value) {
    return '—'
  }
  return value.replace('T', ' ').slice(0, 16)
}
