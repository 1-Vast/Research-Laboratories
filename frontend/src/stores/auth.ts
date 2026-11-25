import { reactive } from 'vue'
import type { MeInfo } from '../types'

/**
 * 认证状态：access token 只放在内存与 sessionStorage（刷新页面可恢复），
 * refresh token 由后端写入 HttpOnly Cookie，前端脚本读不到。
 */
export const authState = reactive<{
  token: string | null
  user: MeInfo | null
}>({
  token: sessionStorage.getItem('labflow_access_token'),
  user: null,
})

export function setToken(token: string | null) {
  authState.token = token
  if (token) {
    sessionStorage.setItem('labflow_access_token', token)
  } else {
    sessionStorage.removeItem('labflow_access_token')
  }
}

export function setUser(user: MeInfo | null) {
  authState.user = user
}

export function clearAuth() {
  setToken(null)
  setUser(null)
}

export function hasPermission(code: string): boolean {
  return authState.user?.permissions.includes(code) ?? false
}
