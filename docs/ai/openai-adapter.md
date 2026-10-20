# 작업 11 — OpenAI 모델 어댑터

상태: 공통 Provider 포트 구현 및 모의 검증 완료. 실제 계정·키·모델 호출 검증은 미수행.
최초 실연결 공급자는 계속 Gemini이며 환경변수 입력은 보류한다.
14번 이후 실제 실행에는 키·모델 ID뿐 아니라 [모델 카탈로그 등록](model-catalog.md)이 필요하다.

## 구현

`app/providers/openai_provider.py`의 OpenAIProvider가 10번의 입력·결과 포트를 구현한다.
이미 의존하는 HTTPX로 고정 HTTPS Responses API를 호출한다. OpenAI SDK나
langchain-openai 의존성을 새로 추가하지 않았으며 LangChain 메시지/SDK 객체를 공통 포트에 노출하지 않는다.
Gemini의 기존 LangChain 구현은 그대로 유지한다. 향후 체인 구성에서도 공급자 선택 경계는
Provider 포트를 사용하며 이번 작업에 별도 LangChain OpenAI 체인을 추가하지는 않았다.

- `POST https://api.openai.com/v1/responses`로 PDF 세 개를 base64 input_file로 전달한다.
  Files API에 업로드하거나 영구 file_id를 만들지 않는다.
- 원본 파일명 대신 문서 UUID로 만든 파일명을 사용하고 문서 종류·ID를 함께 전달한다.
- 기존 JEV 프롬프트와 Findings JSON Schema를 사용하고 `strict=true`로 요청한다.
- `store=false`, `stream=false`, 출력 상한 16,000토큰, HTTPX 단계별 타임아웃 90초다.
  전체 요청의 엄밀한 wall-clock 제한이나 가격 계산은 아직 없다.
- 자동 재시도·폴백·리다이렉트는 하지 않는다. 환경의 프록시/자격증명은 상속하지 않는다.
  따라서 프록시가 필요한 배포 지원은 별도 작업이다.
- 완료 상태와 하나의 구조화 출력만 수용하고 거절·불완전·비정상 JSON·잘못된 근거를 거부한다.
  토큰 수는 공통 결과의 음이 아닌 정수 검증을 거친다. 사용량 미제공은 0으로 표시한다.
- 타임아웃은 ProviderTimeout, 잘못된 출력은 InvalidProviderResponse,
  인증/한도/기타 HTTP·연결 장애는 ProviderFailure로 변환한다.
  상세 429 정책·재시도는 16~17번에서 다룬다. 공급자 원문 오류는 응답·로그에 기록하지 않는다.

## 선택과 설정

로컬에서만 명시적으로 `JEV_PROVIDER=openai`를 선택할 수 있다.
필수 항목은 JEV_SERVICE_TOKEN, OPENAI_API_KEY, OPENAI_MODEL이며 모델 기본값은 없다.
PDF 입력과 구조화 출력을 지원하고 해당 계정에서 사용 가능한 모델 ID를 나중에 지정한다.
공급자별 모델 카탈로그와 capability 검사는 14번 범위다.
기본 local/stub와 Docker production/gemini는 변경하지 않았다.
staging/production에서 OpenAI 선택은 여전히 거부한다.

OPENAI_API_KEY는 SecretStr·repr 제외·표준 로그 마스킹 대상이다.
서비스 토큰과 동일한 값이나 공백 포함 키는 거부한다.
이번 변경은 빈 `.env.example` 항목만 추가했고 실제 `.env`와 키는 생성·입력하지 않았다.
Spring HTTP 클라이언트는 provider=openai 결과도 허용하고 기존 저장 흐름을 사용한다.
사용자가 요청 본문으로 공급자를 선택하거나 키를 전달하는 API는 추가하지 않았다.

## 개인정보와 실제 연결 전 조건

LangSmith 자동 추적 차단과 안전 메타데이터 기록은 기존 워크플로에서 그대로 적용한다.
요청·응답 원문을 로그나 LangSmith에 저장하지 않는다. 실제 호출을 활성화하면 PDF 원문은
OpenAI에 전송되므로, 공급자 사용·데이터 처리 조건을 확인하기 전에는 의료 문서를 보내지 않는다.

공식 문서상 `store=false`는 모든 공급자 측 보존이 없다는 보장이 아니다.
기본 abuse-monitoring 로그에는 고객 콘텐츠가 포함될 수 있고 별도 보존 정책이 적용된다.
Zero Data Retention 등은 자격·승인과 기능별 제한 확인이 필요하다.
참고: [OpenAI 데이터 제어](https://developers.openai.com/api/docs/guides/your-data).

요청 형식의 근거는 [공식 PDF 입력](https://developers.openai.com/api/docs/guides/file-inputs)과
[공식 구조화 출력](https://developers.openai.com/api/docs/guides/structured-outputs)이다.
규정 파일은 계속 비어 있으며 결과는 항상 ON_HOLD/NOT_REGISTERED다.

## 검증

HTTPX MockTransport로 요청의 PDF·스키마·저장 옵션·인증 헤더와 단일 호출을 검사한다.
성공 결과·토큰·규정 보류 상태, 거절·불완전·잘못된 스키마/근거/사용량,
인증 실패·429·서버 오류·연결/타임아웃 및 비밀정보 마스킹을 확인한다.
Spring 모의 HTTP 서버에서도 Gemini/OpenAI 결과를 모두 수용하는지 확인한다.
테스트에는 실제 키·환자 문서·유료 모델 호출을 사용하지 않는다.

2026-10-20: Python 3.11·3.14 각각 126개 테스트 및 Ruff·mypy·포맷 검사 통과.
FastAPI 내부 HTTP 라우트의 OpenAI 성공 계약도 모의 어댑터로 검증했다.
Spring 전체 276개 테스트 통과(실패·오류 0건). 실제 공급자의 모델 지원 여부와
계정 인증, 실문서 판정 품질, 운영 배포는 아직 검증하지 않았다.
