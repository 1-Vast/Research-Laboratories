import http from './http'
import type { ApiResponse, AuditEventInfo, PageResult, PermissionInfo, RoleInfo, UserInfo } from '../types'

export async function listUsers(params: {
  keyword?: string
  page: number
  size: number
}): Promise<PageResult<UserInfo>> {
  const { data } = await http.get<ApiResponse<PageResult<UserInfo>>>('/admin/users', { params })
  return data.data
}

export async function createUser(payload: {
  username: string
  password: string
  displayName?: string
  email?: string
  roles?: string[]
}): Promise<UserInfo> {
  const { data } = await http.post<ApiResponse<UserInfo>>('/admin/users', payload)
  return data.data
}

export async function setUserEnabled(id: number, enabled: boolean): Promise<UserInfo> {
  const { data } = await http.patch<ApiResponse<UserInfo>>(`/admin/users/${id}/enabled`, { enabled })
  return data.data
}

export async function assignUserRoles(id: number, roles: string[]): Promise<UserInfo> {
  const { data } = await http.put<ApiResponse<UserInfo>>(`/admin/users/${id}/roles`, { roles })
  return data.data
}

export async function listRoles(params: {
  keyword?: string
  page: number
  size: number
}): Promise<PageResult<RoleInfo>> {
  const { data } = await http.get<ApiResponse<PageResult<RoleInfo>>>('/admin/roles', { params })
  return data.data
}

export async function createRole(payload: {
  code: string
  name: string
  description?: string
}): Promise<RoleInfo> {
  const { data } = await http.post<ApiResponse<RoleInfo>>('/admin/roles', payload)
  return data.data
}

export async function listPermissions(): Promise<PermissionInfo[]> {
  const { data } = await http.get<ApiResponse<PermissionInfo[]>>('/admin/permissions')
  return data.data
}

export async function assignRolePermissions(id: number, permissions: string[]): Promise<RoleInfo> {
  const { data } = await http.put<ApiResponse<RoleInfo>>(`/admin/roles/${id}/permissions`, { permissions })
  return data.data
}

export async function deleteRole(id: number): Promise<void> {
  await http.delete(`/admin/roles/${id}`)
}

export async function deleteUser(id: number): Promise<void> {
  await http.delete(`/admin/users/${id}`)
}

export async function updateUser(
  id: number,
  payload: { displayName?: string; email?: string },
): Promise<UserInfo> {
  const { data } = await http.patch<ApiResponse<UserInfo>>(`/admin/users/${id}`, payload)
  return data.data
}

export async function listAuditEvents(params: {
  action?: string
  actorId?: number
  page: number
  size: number
}): Promise<PageResult<AuditEventInfo>> {
  const { data } = await http.get<ApiResponse<PageResult<AuditEventInfo>>>('/admin/audit-events', { params })
  return data.data
}
