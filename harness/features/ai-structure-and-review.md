# 기능: AI 중개와 의료 서류 JEV 분석

## 우선순위와 현재 경로

최신 범위는 [유스케이스 우선순위와 MVP 기준](../../docs/ai/use-case-priorities.md)을 따른다.
의료 PDF 세 개의 JEV 작성 상태 점검·근거·신뢰도·보완사항이 P0다.
요약·평문화는 P1, 규정 기반 제출 판단과 약관 검색 답변은 자료 제공 후 P2다.
규정은 미등록이며 JEV의 최종 제출 상태는 항상 ON_HOLD다.

| AI 기능 | 상태 | 구현 |
|---|---|---|
| 의료 PDF JEV 분석 | 구현·실연결 검증 | Spring `JevController` → FastAPI(빠른 검사 → J 멀티 에이전트 → E → V) → Spring 결과 저장·조회 |
| 양식 입력 구조화 | 규칙 기반 | `RuleBasedAiStructurePort` |
| 제출물 요약·보완·의심사항 | JEV 결과에 포함 | FastAPI 요약 에이전트 |
| 약관 RAG | 확인할 조항 설명까지 | FastAPI 약관 에이전트 + pgvector |

## JEV 연결

JEV는 [실행 안내](../../docs/ai/jev-cycle-acceptance.md)에 따라 Gemini·FastAPI·LangSmith를 설정한다.
모델 키와 서비스 토큰을 저장소에 넣지 않는다. Spring 은 LLM 을 직접 호출하지 않는다.

## 공개 API

```
POST /ai/jev                 → 저장된 analysisId와 JEV 결과
GET /ai/jev/{analysisId}      → 권한 확인 후 저장 결과
```

- JEV 상세 요청·응답은 [API 계약](../../docs/ai/jev-contract.md)을 따른다.
- 기존 양식 구조화는 제출 서비스의 포트 호출이며 독립 `/ai/structure` API 구현으로 간주하지 않는다.
- 현재 Spring의 `BusinessException(INTERNAL_ERROR)`는 500이다. 세부 오류 매핑은 후속 작업이다.
- 기존 구조화 실패는 원본 제출을 막지 않고, 평문화 실패는 사정사의 결정 등록을 막지 않는다.

## 원칙

- AI 응답은 추천이며 최종 판단은 사람이 수행한다.
- JEV는 업무 청구 상태나 최종 Decision을 자동 변경하지 않는다.
- JEV의 모델 호출·프롬프트·출력 검증은 FastAPI, 업무 권한·영구 저장은 Spring이 담당한다.
- LangSmith에는 원문 없이 허용된 운영 정보만 전송한다.
