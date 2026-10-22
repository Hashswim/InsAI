import axios from 'axios'
import { session } from './session'

const BASE = import.meta.env.VITE_API_BASE_URL || '/api'

const api = axios.create({ baseURL: BASE, timeout: 15000 })

// 요청: Bearer 토큰 + X-User-Id 주입
api.interceptors.request.use((config) => {
  if (session.accessToken) config.headers.Authorization = `Bearer ${session.accessToken}`
  const u = session.user
  if (u?.id && !config.headers['X-User-Id']) config.headers['X-User-Id'] = u.id
  return config
})

let refreshing = null
async function doRefresh() {
  if (!session.refreshToken) throw new Error('no refresh token')
  const { data } = await axios.post(`${BASE}/auth/refresh`, {
    refreshToken: session.refreshToken,
  })
  const tp = data?.data ?? data
  session.set({ accessToken: tp.accessToken, refreshToken: tp.refreshToken })
}

// 응답: {success,data,error} 봉투 해제 + 401 시 refresh 1회
api.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body === 'object' && 'success' in body) {
      if (body.success) return body.data
      const e = new Error(body.error?.message || '요청을 처리하지 못했습니다.')
      e.code = body.error?.code
      throw e
    }
    return body
  },
  async (err) => {
    const original = err.config || {}
    const status = err.response?.status

    if (status === 401 && session.refreshToken && !original.__retried) {
      original.__retried = true
      try {
        refreshing = refreshing || doRefresh()
        await refreshing
        refreshing = null
        return api(original)
      } catch {
        refreshing = null
        session.clear()
        if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
          window.location.assign('/login')
        }
      }
    }

    const body = err.response?.data?.error
    const e = new Error(body?.message || err.message || '요청을 처리하지 못했습니다.')
    e.code = body?.code
    e.status = status
    return Promise.reject(e)
  },
)

export default api
