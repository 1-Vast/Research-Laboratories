import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import type { ApiResponse, LoginResult } from '../types'
import { authState, clearAuth, setToken } from '../stores/auth'

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  withCredentials: true,
})

http.interceptors.request.use((config) => {
  if (authState.token) {
    config.headers.Authorization = `Bearer ${authState.token}`
  }
  return config
})

let refreshing: Promise<string | null> | null = null

async function refreshAccessToken(): Promise<string | null> {
  try {
    const { data } = await axios.post<ApiResponse<LoginResult>>('/api/v1/auth/refresh', null, {
      withCredentials: true,
    })
    setToken(data.data.accessToken)
    return data.data.accessToken
  } catch {
    clearAuth()
    return null
  }
}

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined
    const status = error.response?.status
    const isAuthRequest = original?.url?.includes('/auth/')

    if (status === 401 && original && !original._retried && !isAuthRequest) {
      original._retried = true
      refreshing = refreshing ?? refreshAccessToken()
      const token = await refreshing
      refreshing = null
      if (token) {
        original.headers = { ...(original.headers ?? {}), Authorization: `Bearer ${token}` }
        return http.request(original)
      }
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  },
)

/** 从统一错误结构中提取可展示的提示，兼容字段级校验错误。 */
export function apiErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as
      | (ApiResponse<Array<{ field: string; message: string }>> & { data?: unknown })
      | undefined
    if (body?.data && Array.isArray(body.data) && body.data.length > 0) {
      return body.data.map((item) => `${item.field}: ${item.message}`).join('；')
    }
    if (body?.message) {
      return body.message
    }
    if (error.response?.status === 401) {
      return '登录状态已失效，请重新登录'
    }
    if (error.response?.status === 403) {
      return '没有执行该操作的权限'
    }
    if (error.response?.status === 429) {
      return '操作过于频繁，请稍后再试'
    }
    return error.message
  }
  return '请求失败，请稍后重试'
}

export default http
