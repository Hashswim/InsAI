// 케이스별 "확인함" 상태 — 로컬 전용 (백엔드 read-receipt 없음)
const KEY = (caseId) => `insai.read.${caseId}`

function load(caseId) {
  try {
    return new Set(JSON.parse(localStorage.getItem(KEY(caseId)) || '[]'))
  } catch {
    return new Set()
  }
}

export function isRead(caseId, itemKey) {
  return load(caseId).has(itemKey)
}

export function markRead(caseId, itemKey) {
  try {
    const s = load(caseId)
    s.add(itemKey)
    localStorage.setItem(KEY(caseId), JSON.stringify([...s]))
  } catch {
    /* 무시 */
  }
}
