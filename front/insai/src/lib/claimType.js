export const CLAIM_TYPES = [
  { code: 'DEATH', label: '사망보험금' },
  { code: 'DISABILITY', label: '후유장해보험금' },
  { code: 'DIAGNOSIS', label: '진단보험금' },
  { code: 'HOSPITALIZATION_SURGERY', label: '입원·수술급여금' },
]

const MAP = Object.fromEntries(CLAIM_TYPES.map((t) => [t.code, t.label]))
MAP.OTHER = '기타'

export function claimTypeLabel(code) {
  return MAP[code] || code || '기타'
}
