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
