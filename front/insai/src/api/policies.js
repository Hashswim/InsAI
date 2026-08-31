import api from './client'

// 내가 가입한 보험 계약 목록
export const listMyPolicies = () => api.get('/policies')

// 데모 보험 계약 심기 (백엔드 dev 프로파일 전용, 이미 있으면 무시)
export const seedDemoPolicies = () => api.post('/dev/seed-policies')
