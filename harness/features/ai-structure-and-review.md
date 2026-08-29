# 기능: AI 중개 (구조화 · 요약 · 평문 번역)  — 스텁 상태, 키 대기

## 현재

| AI 기능 | 상태 | 구현 |
|---|---|---|
| 입력 구조화 | 동작(스텁) | `form.infrastructure.RuleBasedAiStructurePort` — 입력 에코 + 필수 누락 계산 |
| 제출물 요약·하이라이트·누락 | 미착수 | `ai.application.ReviewSummaryService` (예정) |
| 사정 결과 평문 번역 + 약관 근거 | 미착수 (2차) | `Decision.plainText` 채우는 경로 없음 |
| 문의 1차 응답 (약관 RAG) | 미착수 (2차) | `knowledge` 모듈 후보 |

## 연결 지점 (키 수령 시)

`ai` 모듈 문서 [modules/back-ai.md](../modules/back-ai.md) 참조. 요약:

1. `LlmClient` 포트 + `StubLlmClient`(기본) / `AnthropicLlmClient`(`insai.ai.enabled=true` 시).
2. `LlmAiStructurePort implements form.application.AiStructurePort` 를 `@Primary @ConditionalOnProperty` 로 → 활성 시 `RuleBasedAiStructurePort` 대체. **form 코드 변경 없음.**
3. env: `INSAI_AI_ENABLED=true INSAI_AI_API_KEY=... INSAI_AI_MODEL=claude-sonnet-5`.
4. `AnthropicLlmClient` HTTP 계약은 `claude-api` 스킬로 모델 ID·엔드포인트·헤더 확인 후 실검증.

## 계약 (응답 형태 고정)

```
POST /ai/structure       → { fields:{key:value}, confidence:{key:0~1}, missingFields:[] }
POST /ai/review-summary   → { summary, highlights:[], issues:[], needsAttention:boolean }
```

- LLM 실패 → `BusinessException(INTERNAL_ERROR)` (원문 예외 미노출), 웹에서 502.
- 구조화 실패는 제출을 막지 않음 (`SubmitFormService` 가 삼킴).

## 원칙

- AI 응답은 **1차 보조**. 최종 판단은 사정사. `needsAttention`/`needsAdjusterReview` 플래그로 표시.
- 프로바이더는 포트 뒤에. 테스트는 `FakeLlmClient`.
- 프롬프트/파싱 로직은 `ai.infrastructure` 의 Llm 백드 impl 안에 격리.
