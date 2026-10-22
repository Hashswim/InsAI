# 모듈: back/insai · com.insuranceai.ai

## 목적

JEV 분석 연동. Spring 은 케이스 참여자 권한·감사·결과 저장을 맡고, 서류 검토(LLM 포함)는 FastAPI JEV 서비스에 위임한다.
Spring 에서 LLM 을 직접 호출하지 않는다 (과거 `insai.ai` Anthropic 경로는 제거).

## 경로

- `back/insai/src/main/java/com/insuranceai/ai/`
- 설정: `insai.jev.*` (base-url, service-token, timeout-seconds, allow-stub, jobs.worker-enabled)

## 설계

| 타입 | |
|---|---|
| `api.JevController` | `POST /ai/jev`, `GET /ai/jev/{analysisId}`, `GET /ai/jev/cases/{caseId}/latest` — 청구인 응답은 `JevResult.forClaimant()` 로 축소 |
| `api.JevJobController` | 비동기 분석 작업 생성·조회 |
| `api.JevHistoryController` | 분석·작업 실행 감사 이력 (운영용) |
| `api.JevMetricsController` | 케이스별 통계 (대시보드) |
| `application.RunJevService` / `JevJobService` / `JevJobWorker` | PDF 3종 읽기 → `JevGateway` 호출 → 저장 |
| `application.JevGateway` (포트) | 구현: `infrastructure.FastApiJevClient` (서비스 토큰, `JevWireResponseDecoder` 로 응답 검증) |
| `domain.*` | `JevAnalysis`, `JevJob`, `JevAccessAudit`, `JevExecutionEvent` |

## 불변식·규칙

- JEV 결과는 항상 ON_HOLD + human_review REQUIRED. 최종 결정은 손해사정사.
- 청구인 응답에서 요약·의심사항·약관 조항·항목 상세를 제외한다.
- 응답은 FastAPI 와 공유 계약(`docs/ai/contracts/`)으로 디코딩하고, 어긋나면 저장하지 않는다.
