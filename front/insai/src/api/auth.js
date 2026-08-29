import api from './client'

export const login = (email, password) => api.post('/auth/login', { email, password })
export const signup = (payload) => api.post('/auth/signup', payload)
export const logout = (refreshToken) => api.post('/auth/logout', { refreshToken })

// 로컬 데모용 — 백엔드 dev 프로파일에서만 동작
export const devActivate = (email) => api.post('/dev/activate', { email })
// 자격증명 없이 역할만으로 로그인 (dev 전용)
export const demoLogin = (role) => api.post('/dev/demo-login', { role })
