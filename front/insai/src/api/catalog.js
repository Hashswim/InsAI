import api from './client'

export const claimTypes = () => api.get('/catalog/life/claim-types')
export const requiredDocuments = (type) => api.get(`/catalog/life/claim-types/${type}/required-documents`)
