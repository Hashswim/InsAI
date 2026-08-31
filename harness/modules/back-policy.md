# 모듈: back/insai · com.insuranceai.policy  (구현 완료 — GREEN)

## 목적

청구인이 **가입한 보험 계약(증권)** 조회. 현재는 생명보험 계열만.
홈 화면 "내 보험 조회하기" → `/policies` 목록. 청구 시작의 진입점 역할도 겸함.
**책임 밖**: 청구 케이스(collab), 서류(form/document), 보험금 계산.

## 경로

- `back/insai/src/main/java/com/insuranceai/policy/`
- 스키마: `V5__policy.sql` — `policies`

## 설계

| 도메인 | |
|---|---|
| `Policy` (BaseEntity) | ownerId(청구인), insurerName, productName, policyNumber(unique), planType, insuredName, contractDate, coverageStartDate, coverageEndDate?, coverageAmount, monthlyPremium, coverageSummary?, status |
| `PolicyPlanType` | WHOLE_LIFE 종신 / TERM_LIFE 정기 / CI / CANCER 암 / ANNUITY 연금 / ENDOWMENT 저축성 / OTHER |
| `PolicyStatus` | ACTIVE 정상 / LAPSED 실효 / EXPIRED 만기 / TERMINATED 해지 |
| `Policy.register(...)` | 텍스트 필수값 공백 → INVALID_INPUT |

| 서비스 | |
|---|---|
| `ListMyPoliciesService.list(ownerId)` | `findByOwnerIdOrderByContractDateDesc` → `List<PolicyView>` (enum 라벨 포함) |
| `SeedDemoPoliciesService.seedFor(ownerId, insuredName)` | 계약이 없을 때만 데모 3건(종신·암·정기). 증권번호는 ownerId 파생으로 충돌 회피. `ownerId==null` 이면 조회도 안 함 |

| API | |
|---|---|
| `GET /policies` | X-User-Id → `List<PolicyView>` (본인 계약, 계약일 내림차순) |
| `POST /dev/seed-policies` | (dev) X-User-Id 청구인에게 데모 계약 심기 — idempotent |

## 의존 관계

- 나감: `common` 만.
- `dev` 모듈이 `SeedDemoPoliciesService` 를 호출 — `demo-login`(CLAIMANT) 시 자동 시드 + `/dev/seed-policies`.
- collab/form/document 와 직접 연결 없음(느슨). 향후 케이스↔증권 링크는 별도.

## 주의 (gotchas)

- 데이터 출처: 정식 보험사 연동 전까지는 **데모 시드뿐**. prod 프로파일엔 시드 경로 없음.
- `policy_number` 전역 unique → 데모 시드는 `HL-<owner8>-01` 식으로 파생.
- 프론트: 홈 "내 보험 조회하기"는 미인증 시 청구인 데모 로그인 후 `/policies`. 빈 목록이면 "데모 보험 불러오기" 버튼(→ `/dev/seed-policies`).

## 상태·TODO

- [x] 전 계층 (GREEN) — `+10` tests (총 227/227)
- [x] `V5__policy.sql`
- [x] `dev` demo-login 시드 훅 + `/dev/seed-policies`
- [x] 프론트 `PoliciesView` + `/policies` 라우트 + 홈 CTA + 헤더 내비
- [ ] 보험사 연동 또는 청구인 직접 등록(신규 등록 폼)
- [ ] 청구 케이스에서 증권 선택·연결
