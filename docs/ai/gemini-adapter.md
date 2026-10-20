# 작업 12 — Gemini 어댑터 점검·보완

상태: 기존 LangChain Gemini 어댑터 보완 및 로컬 자동 검증 완료.
실제 키 입력·모델 호출·실문서 품질 검증은 수행하지 않았다.
최초 실연결 모델 공급자는 계속 Gemini이고 규정 파일은 비어 있다.

## 입력과 연결 설정

`app/providers/gemini_provider.py`가 공통 ProviderRequest를 받아 ProviderResult를 반환한다.
진단서·진료확인서·병원비 세부내역서의 검증된 PDF base64를 LangChain 파일 블록으로
전달하고 문서 ID·종류와 기존 JEV 프롬프트를 포함한다. 외부 URL 접근이나 Files API 업로드는 하지 않는다.
API 입력 제한인 문서당 10MiB·30페이지와 세 종류 각 한 개 규칙은 유지한다.

- Gemini Developer API를 명시적으로 사용한다(`vertexai=False`).
- 연결 주소는 `https://generativelanguage.googleapis.com`으로 지정한다.
- 환경의 프록시/인증 설정을 상속하지 않고 리다이렉트를 따라가지 않는다.
  프록시가 필요한 배포는 별도 지원이 필요하다.
- 모델 ID는 기존 GEMINI_MODEL을 사용하며 임의 기본 모델은 지정하지 않는다.
  모델의 PDF·구조화 출력 지원과 계정 이용 권한은 실연결 전 확인해야 한다.
- 기존 온도 0, 타임아웃 90초를 유지하고 출력 상한 16,000토큰을 지정했다.
  90초는 전체 작업의 엄밀한 wall-clock 제한을 의미하지 않는다.

## 단일 시도

설치된 LangChain Google SDK의 옵션 설명을 확인하고 `max_retries=0`을
총 한 번 시도하는 `max_retries=1`로 바꿨다. 이 옵션은 일반적인 ‘추가 재시도 횟수’와 다르다.
앱 레벨 재시도·폴백은 추가하지 않았고 HTTP 모의 전송에서 429·503·504도 한 번만 호출함을 검증했다.
추후 재시도 정책은 16번에서 구현한다.

검증한 설치 버전은 langchain-google-genai 4.4.2 / google-genai 2.31.0이다.
pyproject 최소 버전을 이에 맞추고 직접 사용하는 google-genai를 명시 의존성으로 등록했다.
전체 전이 의존성을 고정한 lock 파일은 아직 없으므로 다른 SDK 버전은 CI에서 다시 검증해야 한다.

## 구조화 응답 검증

LangChain의 `with_structured_output(Findings, method="json_schema", include_raw=True)`를 사용한다.
raw 응답은 메모리에서 정상 종료와 사용량을 확인하는 데만 쓰며 로그·LangSmith에 저장하지 않는다.

- 파싱 오류가 없고 Findings 결과가 있어야 한다.
- raw는 AIMessage여야 하고, 내용이 비어 있거나 tool call이 있으면 거부한다.
- finish_reason은 STOP만 수용한다. MAX_TOKENS·SAFETY·RECITATION·누락 등은 실패다.
- prompt_feedback에 차단 사유가 있으면 결과를 반환하지 않는다.
- 파싱된 모델/딕셔너리를 Findings로 다시 검증한다.
- 사용량은 공통 결과 검증을 거친 음이 아닌 정수여야 한다.
  사용량 미제공은 0이며 무료 호출이나 비용 0을 보장하는 뜻은 아니다.
- 기존 워크플로가 근거의 문서 ID·페이지 범위를 재검증한다.

부분 결과를 정상 판단으로 저장하지 않는다. 최종 제출 상태는 계속 ON_HOLD/NOT_REGISTERED이며
신뢰도는 보정된 확률이나 독립적인 사실 검증을 뜻하지 않는다.

## 오류와 로그

HTTPX/기본 타임아웃과 Google API 오류 408·504는 ProviderTimeout으로 변환한다.
파싱·출력 검증 오류는 InvalidProviderResponse, 인증·429·기타 SDK 장애는 ProviderFailure다.
기존 504/502 API 계약으로 변환하며 외부 오류 메시지나 원문을 응답에 포함하지 않는다.
Google/LangChain SDK의 표준 로그에는 원문 피드백이 포함될 수 있어 앱 lifespan의
로그 팩토리가 해당 이름공간의 메시지·예외·스택을 고정 문구로 치환한다.
로그 레벨과 로거 이름은 남기고 `Provider SDK diagnostic suppressed`로 표시한다.
전체 SDK 로그가 억제되므로 상세 공급자 진단 정보는 줄어든다.

이는 임의의 print·사용자 지정 로그 extra·외부 프록시까지 막는 보장이 아니다.
LangSmith 자동 추적 차단과 기존 안전 메타데이터 기록은 유지한다.
실제 호출 시 의료 PDF는 Google에 전송되므로 공급자 데이터 처리 조건과 계정 정책 확인이 별도로 필요하다.

## 검증

단위 테스트는 정상 입력, 고정 연결 옵션, 키/모델 누락, SDK 오류 변환,
부분/차단 응답, raw/parsed/usage 오류를 검사한다.
통합 테스트는 실제 LangChain·Google SDK를 사용하되 HTTPX MockTransport로 네트워크를 대체한다.
실제 환자 정보 없이 생성한 빈 PDF의 바이트가 SDK 전송 후에도 일치함을 확인한다.
SDK의 MIME 키 표기와 base64 직렬화 차이는 PDF 바이트로 비교한다.
FastAPI 내부 HTTP 성공 응답, 토큰 수, 단일 시도, 비정상 종료와 SDK 로그 치환을 검증한다.
이 검증은 Google 서버의 실제 스키마 수용·계정 인증·모델 지원 여부나 판단 품질을 보증하지 않는다.

2026-10-20: Python 3.11·3.14 각각 166개 테스트 통과.
Ruff·mypy(22개 소스)·포맷 검사 통과. Python 3.14에서 기존 Google SDK의
DeprecationWarning 1건은 남아 있다. Spring·프론트엔드·실제 환경변수는 변경하지 않았다.

공식 참고: [LangChain Gemini 통합](https://docs.langchain.com/oss/python/integrations/chat/google_generative_ai),
[Google Gen AI SDK](https://googleapis.github.io/python-genai/).
