import api from './client'

// 업로드: (1) upload-url 발급 → (2) 그 URL 로 PUT → (3) /files 로 확정 → fileId 반환
export async function uploadFile({ caseId, submissionId = null, file }) {
  const meta = {
    caseId,
    submissionId,
    filename: file.name,
    contentType: file.type || 'application/octet-stream',
    sizeBytes: file.size,
  }
  const { url, storageKey } = await api.post('/files/upload-url', meta)
  await api.put(url, file, { headers: { 'Content-Type': meta.contentType } })
  const { fileId } = await api.post('/files', { ...meta, storageKey })
  return fileId
}

export const listCaseFiles = (caseId) => api.get('/files', { params: { caseId } })
export const getDownloadUrl = (fileId) => api.get(`/files/${fileId}/download-url`)

// 브라우저에서 첨부 파일 열기 (다운로드 URL 발급 후 새 탭)
export async function openFile(fileId) {
  const { url } = await getDownloadUrl(fileId)
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  window.open(base + url, '_blank')
}
