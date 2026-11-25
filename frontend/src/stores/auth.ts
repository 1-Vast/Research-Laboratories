import { reactive } from 'vue'
import type { MeInfo } from '../types'

/**
 * 认证状态：access token 只保存在内存，刷新页面后由 HttpOnly 刷新 Cookie 静默换新；
 * refresh token 前端脚本不可读，降低 XSS 窃取风险。
 */
export const authState = reactive<{
  token: string | null
  user: MeInfo | null
}>({
  token: null,
  user: null,
})

export function setToken(token: string | null) {
  authState.token = token
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
