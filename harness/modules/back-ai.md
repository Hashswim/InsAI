# 모듈: back/insai · com.insuranceai.ai  (예정 — 미착수, 키 대기)

## 목적

LLM 오케스트레이션. 입력 구조화, 제출물 요약·하이라이트·누락 탐지, (2차) 사정 결과 평문 번역·약관 기반 1차 응답.
**API 키를 받으면 연결** — 그 전까지 포트 + 스텁으로 나머지 기능이 동작하도록 구현한다.

## 경로 (계획)

- `back/insai/src/main/java/com/insuranceai/ai/`
- 설정: `insai.ai.enabled`, `insai.ai.provider`, `insai.ai.api-key`, `insai.ai.model` (기본 비활성)

## 설계

| 타입 | |
|---|---|
| `LlmClient` (포트) | `complete(LlmRequest)` → 원시 텍스트. `LlmRequest(task, systemPrompt, userPrompt)` |
| `infrastructure.StubLlmClient` | 기본 활성. task 별 고정 JSON 반환 (계약 만족). `@ConditionalOnProperty("insai.ai.enabled", havingValue="false", matchIfMissing=true)` |
| `infrastructure.AnthropicLlmClient` | `@ConditionalOnProperty("insai.ai.enabled"=true)`. RestClient 로 Claude Messages API 호출. **키 수령 후 검증** |
| `application.StructureService` | rawInput+template → 구조화 (form 의 AiStructurePort 실물 후보) |
| `application.ReviewSummaryService` | 제출 data + OCR 텍스트 → `{summary, highlights[], issues[], needsAttention}` |
| `application.ReviewSummarizer` (포트) | Stub / Llm 구현 |
| `infrastructure.LlmAiStructurePort implements form.application.AiStructurePort` | `@Primary @ConditionalOnProperty(...enabled=true)` — 활성 시 `RuleBasedAiStructurePort` 대체 |
| `api.AiController` | `POST /ai/structure`, `POST /ai/review-summary` |

## 연결 절차 (키 수령 시)

1. `application-dev.yml` 또는 env: `INSAI_AI_ENABLED=true`, `INSAI_AI_API_KEY=...`, `INSAI_AI_MODEL=claude-sonnet-5`
2. `AnthropicLlmClient` 빈 활성 → `LlmAiStructurePort`/`LlmReviewSummarizer` 가 `@Primary` 로 승격
3. `AnthropicLlmClient` HTTP 계약 실검증 (모델 ID·엔드포인트·헤더 — `claude-api` 스킬 참고)
4. `ReviewSummaryService` 프롬프트·파싱 튜닝

## 불변식·규칙

- LLM 실패(타임아웃/에러) → `BusinessException(INTERNAL_ERROR)` 로 변환, 원문 예외 미누출.
- `SubmitFormService` 는 이미 AI 실패를 삼키고 원본으로 제출 성립 → structure 실패가 제출을 막지 않음.
- 프로바이더 교체는 포트만으로 (`FakeLlmClient` 주입 테스트).

## 테스트 (계획)

`FakeLlmClient` 로 Llm 백드 impl 의 프롬프트/파싱, Stub impl 의 계약, `AiController` 슬라이스(입력 누락 400, LLM 실패 502).

## 상태·TODO

- [ ] `LlmClient` 포트 + `StubLlmClient`
- [ ] `AnthropicLlmClient` 스켈레톤 (guarded, 키 대기)
- [ ] `ReviewSummaryService` + `/ai/review-summary`
- [ ] `LlmAiStructurePort` → form 연결
- [ ] (2차) translate-decision, 약관 RAG (`knowledge` 모듈 후보)
