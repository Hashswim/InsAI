# 백엔드 작업 단위 진행표

원칙: 기존에 제안한 28개 단위를 순서대로 한 번에 하나 수행하고 결과를 보고한다.
앞선 MVP 구현이 있어도 각 단위의 요구사항을 다시 확인한 뒤 완료 처리한다.
완료는 해당 단위 범위의 완료이며 전체 서비스 운영 준비 완료를 의미하지 않는다.

| 번호 | 작업 단위 | 상태 |
|---|---|---|
| 01 | JEV 결정 모델 명세 작성 | 완료 — [명세와 예제 5건](jev-model-spec.md) |
| 02 | AI의 권한과 금지 행위 정의 | 완료 — [권한·데이터 처리 정책](ai-authority-policy.md) |
| 03 | Spring Boot와 FastAPI 책임 경계 결정 | 완료 — [서비스 책임 경계](service-boundaries.md) |
| 04 | AI 유스케이스 우선순위 확정 | 완료 — [우선순위와 MVP 범위](use-case-priorities.md) |
| 05 | Python AI 서비스 프로젝트 생성 | 완료 — [기반 보완 및 실행 검증](project-foundation.md) |
| 06 | 환경설정 및 비밀정보 관리 | 완료 — [환경 분리·키 주입·마스킹·스텁 검증](configuration-secrets.md) |
| 07 | 기본 운영 엔드포인트 구현 | 완료 — [live·ready·version 및 운영 검사](operations-endpoints.md) |
| 08 | 공통 API 계약 및 오류 규격 정의 | 완료 — [요청 ID·오류 봉투·상태 매핑·공유 계약](api-contract-errors.md) |
| 09 | 서비스 간 인증과 인가 구현 | 완료 — [사용자 JWT·내부 토큰·케이스 인가·접근 감사](service-auth-and-access.md) |
| 10 | 공통 LLM 인터페이스 정의 | 완료 — [내부 입력·구조화 결과·공통 오류 포트](llm-interface.md) |
| 11 | OpenAI 모델 어댑터 구현 | 완료 — [Responses 어댑터·모의 검증](openai-adapter.md); 최초 실연결은 Gemini 유지 |
| 12 | Gemini 모델 어댑터 구현 | 완료 — [단일 시도·정상 종료 검증·실제 SDK 모의 전송](gemini-adapter.md) |
| 13 | AWS Bedrock 도입 | 보류 — 사용자 결정. 관련 실행 코드·전용 의존성 없음 확인 |
| 14 | 모델 카탈로그와 Capability Registry 구현 | 완료 — [선언형 등록·JEV 호환성·SDK 생성 전 차단](model-catalog.md) |
| 15 | 모델 Router 정책 구현 | 완료 — [고정/동일 공급자 우선순위 선택·선택 이유 추적](model-routing.md) |
| 16 | 폴백·재시도·서킷 브레이커 구현 | 완료 — [명시적 복구·호출/시간 상한·모델별 차단·동시성](model-resilience.md) |
| 17 | 비용·속도 제한 정책 구현 | 완료 — [본문 전 요청 제한·실행 제한·선택적 추정 비용 예약](usage-limits.md) |
| 18 | 프롬프트 및 체인 버전 관리 기반 구축 | 완료 — [릴리스 식별·무결성 검증·응답/추적/저장 연결](prompt-chain-versions.md) |
| 19 | 구조화 출력과 파싱 계층 구현 | 완료 — [공통 엄격 파서·원본/SDK 검증·실패 시 복구 금지](structured-output.md) |
| 20 | J 단계 체인 구현 | 완료 — [잠정 판정 경계·의료 점검 프롬프트·중복 범위 차단](judgment-chain.md) |
| 21 | E 단계 근거 수집 체인 구현 | 완료 — [요청 PDF 참조 연결·페이지 범위·항목별 중복 근거 차단](evidence-chain.md) |
| 22 | V 단계 검증 체인 구현 | 완료 — [최종 구조 검사·검증 한계 명시·사람 검토 정책](verification-chain.md); 의미적 사실 검증은 미구현 |
| 23 | JEV 오케스트레이터 구현 | 완료 — [고정 순서·실패 즉시 중단·시도별 상태·재시도 경계](jev-orchestrator.md) |
| 24 | 안전장치 및 입력 방어 구현 | 완료 — [엄격 JSON/HTTP·PDF 기능 수용 정책·진단 로그 차단](input-defense.md); 파서 격리·의미적 공격 방어는 별도 검증 필요 |
| 25 | Spring Boot ↔ FastAPI 내부 클라이언트 구현 | 완료 — [엄격한 RPC 응답 검증·크기/타임아웃 제한·저장 전 차단](internal-client.md) |
| 26 | 비동기 AI 작업과 상태 저장 구현 | 완료 — [DB 큐·202/상태 API·중복 접수 방지·원자적 완료·중단 상태](async-jobs.md); 작업자는 opt-in |
| 27 | 결과 저장과 감사 이력 연결 | 완료 — [결과 출처 메타데이터·원문 없는 실행 장부·원자적 저장·권한별 이력 조회](result-audit.md) |
| 28 | LangSmith 추적·평가·운영 대시보드 구축 | 백엔드 구현 완료 — [추적 전송 경계·합성 평가·권한별 통계 API](observability-dashboard.md); 실제 계정 차트 설정·프론트·실연결 검증 보류 |

현재 사용자 결정: 규정은 비워 둔다. 규정·약관을 임의로 채우지 않는다.
Gemini를 최초 연결 모델로 사용하고 LangSmith에 원문을 저장하지 않는다.
