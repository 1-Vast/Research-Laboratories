import http from './http'
import type { ApiResponse, LoginResult, MeInfo, UserInfo } from '../types'

export async function login(username: string, password: string): Promise<LoginResult> {
  const { data } = await http.post<ApiResponse<LoginResult>>('/auth/login', { username, password })
  return data.data
}

export async function register(payload: {
  username: string
  password: string
  displayName?: string
  email?: string
}): Promise<UserInfo> {
  const { data } = await http.post<ApiResponse<UserInfo>>('/auth/register', payload)
  return data.data
}

export async function logout(): Promise<void> {
  await http.post('/auth/logout')
}

export async function fetchMe(): Promise<MeInfo> {
  const { data } = await http.get<ApiResponse<MeInfo>>('/me')
  return data.data
}

/** 自助改密：成功后后端吊销全部会话，前端需要重新登录。 */
export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await http.put('/me/password', { currentPassword, newPassword })
}

/** 页面刷新后尝试用 HttpOnly Cookie 换取新的 access token。 */
export async function tryRefresh(): Promise<LoginResult | null> {
  try {
    const { data } = await http.post<ApiResponse<LoginResult>>('/auth/refresh')
    return data.data
  } catch {
    return null
  }
}
