import api from './client'

export const listCases = (unassigned = false) =>
  api.get('/cases', { params: unassigned ? { unassigned: true } : {} })
export const getCase = (id) => api.get(`/cases/${id}`)
export const openCase = (title, claimType) => api.post('/cases', { title, claimType })
export const assignAdjuster = (id, adjusterId) => api.patch(`/cases/${id}/assign`, { adjusterId })
export const startReview = (id) => api.post(`/cases/${id}/review`)
export const closeCase = (id) => api.post(`/cases/${id}/close`)
export const getDecision = (id) => api.get(`/cases/${id}/decision`)

export const listDocumentRequests = (id) => api.get(`/cases/${id}/document-requests`)
export const createDocumentRequest = (id, body) => api.post(`/cases/${id}/document-requests`, body)
export const updateDocumentRequest = (drId, action, reason) =>
  api.patch(`/document-requests/${drId}`, { action, reason })

export const listMessages = (id) => api.get(`/cases/${id}/messages`)
export const postMessage = (id, senderRole, body) =>
  api.post(`/cases/${id}/messages`, { senderRole, body })

export const registerDecision = (id, rawText) => api.post(`/cases/${id}/decisions`, { rawText })
export const getTimeline = (id) => api.get(`/cases/${id}/timeline`)
