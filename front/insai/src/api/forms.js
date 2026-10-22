import api from './client'

export const getTemplate = (id) => api.get(`/templates/${id}`)
export const getTemplateByCode = (code) => api.get('/templates', { params: { code } })

export const getSubmissionByDocRequest = (documentRequestId) =>
  api.get('/submissions', { params: { documentRequestId } })

export const submitForm = (body) => api.post('/submissions', body)
export const saveDraft = (body) => api.post('/submissions/draft', body)
