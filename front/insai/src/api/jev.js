import api from './client'

// 동기 분석은 AI 서비스 실행 예산(최대 ~120초)까지 기다린다.
const JEV_TIMEOUT = 130000

export const BUNDLES = {
  OUTPATIENT: {
    label: '외래 (세부내역서·초진기록지·영수증)',
    kinds: ['ITEMIZED_BILL', 'INITIAL_VISIT_RECORD', 'MEDICAL_RECEIPT'],
  },
  LEGACY: {
    label: '진단 (진단서·진료확인서·세부내역서)',
    kinds: ['DIAGNOSIS', 'TREATMENT_CONFIRMATION', 'ITEMIZED_BILL'],
  },
}

export const KIND_LABELS = {
  DIAGNOSIS: '진단서',
  TREATMENT_CONFIRMATION: '진료확인서',
  ITEMIZED_BILL: '진료비 세부내역서',
  INITIAL_VISIT_RECORD: '초진기록지',
  MEDICAL_RECEIPT: '진료비 영수증',
}

export const runJev = (caseId, documents) =>
  api.post('/ai/jev', { caseId, documents }, { timeout: JEV_TIMEOUT })
export const getJev = (analysisId) => api.get(`/ai/jev/${analysisId}`)
export const jevMetrics = (caseId, days = 7) =>
  api.get(`/ai/jev/cases/${caseId}/metrics`, { params: { days } })
