import api from './client'

export const login = (email, password) => api.post('/auth/login', { email, password })
export const signup = (payload) => api.post('/auth/signup', payload)
export const logout = (refreshToken) => api.post('/auth/logout', { refreshToken })

// 로컬 데모용 — 백엔드가 dev 프로파일일 때만 동작 (승인 대기 사정사 즉시 활성화)
export const devActivate = (email) => api.post('/dev/activate', { email })
