# 하네스 문서 인덱스

작업 전 읽는 순서: `README.md` → 해당 `modules/*.md` → 관련 `features/*.md` → 필요 시 `agents/*.md`.

## 상위 (Phase 1·2·5)

| 파일 | 내용 |
|---|---|
| `README.md` | 하네스 엔지니어링 개요, 6단계 매핑, 문서 템플릿 |
| `00-domain.md` | 도메인 분석 — AI 중립 청구 협업, 액터, 생명보험 청구 유형, 비목표 |
| `01-architecture.md` | 모듈러 모놀리스(MSA-ready) 구조, 모듈 경계 규칙, MSA 분리 대상, 인증 현재/목표 |
| `02-workflow.md` | Generate-Validate 라이프사이클, 테스트 계층, Flyway 작성 절차, 로컬 인프라 주의, 커밋 규칙 |
| `conventions.md` | 패키지 구조, 엔티티/에러/응답봉투/행위자/검증/영속성/보안/네이밍/프론트 규약 |

## 모듈 컨텍스트 `modules/` (Phase 4 — 폴더 하나 = 문서 하나)

| 파일 | 대상 폴더 | 상태 |
|---|---|---|
| `modules/back-common.md` | `back/insai/.../common` | ✅ 완료 |
| `modules/back-auth.md` | `back/insai/.../auth` | ✅ 완료 (44 테스트) |
| `modules/back-collab.md` | `back/insai/.../collab` | ✅ 완료 (+ 프론트용 조회 엔드포인트 갭) |
| `modules/back-form.md` | `back/insai/.../form` | ✅ 완료 |
| `modules/back-form-catalog.md` | `back/insai/.../form/catalog` | ✅ 완료 (생명보험 서류 15종) |
| `modules/back-document.md` | `back/insai/.../document` | ⏳ 계획 (미착수) |
| `modules/back-ai.md` | `back/insai/.../ai` | ⏳ 계획 (API 키 대기) |
| `modules/back-infra.md` | gateway/discovery/config | ⏳ 계획 (모놀리스 유지 중) |
| `modules/front-insai.md` | `front/insai` | 🔨 착수 (스캐폴드 정리·의존성 고정 완료) |

## 기능 컨텍스트 `features/` (Phase 4 — 엔드투엔드 시나리오)

| 파일 | 내용 |
|---|---|
| `features/auth-login-signup.md` | 회원가입·로그인·토큰 회전·세션. curl 예시, 갭 |
| `features/claim-case-lifecycle.md` | 케이스 상태 머신, 타임라인, 권한 표, 검증된 흐름, 갭 |
| `features/document-request-and-submission.md` | 서류 요청 → 양식 제출 → 검토 → accept/reject → 재제출 |
| `features/life-insurance-claim-documents.md` | **생명보험 청구 서류 15종 × 칸(FormField) 전체 목록**, 유형별 매트릭스 |
| `features/ai-structure-and-review.md` | AI 중개 현재(스텁)/목표, 키 수령 시 연결 절차 |
| `features/frontend-mvp.md` | 프론트 화면 흐름, 상태/인증 처리, 컴포넌트, 백엔드 블로커 |

## 에이전트 `agents/` (Phase 3 — `.claude/agents/*.md` 형식)

| 파일 | 역할 |
|---|---|
| `agents/analyst.md` | 도메인 분석 → test-plan 케이스 → 영향 식별 (구현 X) |
| `agents/backend-builder.md` | back/insai 기능 RED→GREEN + 마이그레이션 + 로컬 검증 |
| `agents/frontend-builder.md` | front/insai 화면/컴포넌트, 실제 백엔드 연동 확인 |
| `agents/qa.md` | 병합 전 검증 게이트 (테스트 + 구동 + 회귀) |

## 파생 문서

| 위치 | 관계 |
|---|---|
| `docs/test-plan.md` | 하네스가 참조하는 TDD 케이스 대장 (analyst 가 갱신, qa 가 대조) |
| `README.md` (repo 루트) | 프로젝트 개요 |
