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

/** 최근 AI 점검의 보완 제안을 보완 요청 문구 초안으로 만든다. 없으면 빈 문자열. */
export async function aiSupplementText(caseId) {
  try {
    const latest = await getLatestJev(caseId)
    return (latest?.result.items ?? [])
      .filter((it) => it.judgment === 'NEEDS_SUPPLEMENT' && it.supplement)
      .map((it) => `${it.item}: ${it.supplement}`)
      .join(' / ')
  } catch {
    return ''
  }
}

export const runJev = (caseId, documents) =>
  api.post('/ai/jev', { caseId, documents }, { timeout: JEV_TIMEOUT })
// 청구 건의 가장 최근 분석 (누가 실행했든). 청구인에게는 손해사정사용 요약·의심사항이 빠진 채 온다.
export const getLatestJev = (caseId) => api.get(`/ai/jev/cases/${caseId}/latest`)
export const jevMetrics = (caseId, days = 7) =>
  api.get(`/ai/jev/cases/${caseId}/metrics`, { params: { days } })
