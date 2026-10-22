# 작업 10 — 공통 LLM 인터페이스

현재 JEV 유스케이스에 필요한 동기 구조화 출력 포트를 정의했다.
범용 채팅·스트리밍·도구 호출 API까지 미리 구현하는 인터페이스는 아니다.
외부 Spring/FastAPI JSON 계약은 변경하지 않는다.

## 호출 경계

`JevRequest → run_workflow → ProviderRequest → Provider.analyze → ProviderResult`

- `app/providers/base.py`의 `Provider`는 Python Protocol이다. 공급자는 상속 없이
  `name`, `model`, `analyze(request)`를 구현한다.
- `ProviderRequest`는 요청 ID와 문서 튜플만 가진 불변 내부 입력이다.
  각 `ProviderDocument`는 문서 ID·종류·이미 검증한 PDF base64를 가진다.
  HTTP 헤더, 사용자 ID, 역할, 케이스 ID, 키, SDK 객체는 전달하지 않는다.
- 입력 변환은 워크플로에서 한다. PDF 유효성 검사는 기존 API 입력 계층에 남는다.
  내부 DTO 자체는 PDF를 다시 파싱하지 않으므로 검증된 입력만 만들어야 한다.
- `ProviderResult`는 Findings와 입력/출력 토큰 수를 반환한다.
  토큰 수는 음이 아닌 정수만 허용하며 bool·문자열·실수를 거부한다.
  사용량 미제공 시 0은 비용이 없다는 보증이 아니라 사용량 미확인을 포함한다.
- 결과의 외부 컨테이너는 불변이지만 Findings는 깊은 불변 객체가 아니다.
  워크플로가 Findings를 다시 검증·복사하고 문서/페이지 근거를 확인한 새 결과를 반환한다.

## 어댑터 책임

Gemini·스텁을 동일 포트에 맞췄다. Gemini SDK와 메시지·PDF 블록 구성 및
구조화 응답 파싱은 Gemini 어댑터에 남긴다. LangChain 객체는 공통 포트에 노출하지 않는다.
선택은 `build_provider`/`build_routed_provider`가 담당하며
15번의 [서버 측 라우팅 정책](model-routing.md)을 사용한다.

한 번의 호출은 한 번의 시도다. 공통 포트에 재시도·폴백·DB 저장·사용자 인가를 넣지 않는다.
Gemini 타임아웃은 90초이며 자동 재시도는 하지 않는다.
12번 점검에서 SDK 옵션은 총 1회 시도인 `max_retries=1`로 명시했다.

## 오류와 개인정보

어댑터는 공급자와 무관한 `ProviderTimeout`, `InvalidProviderResponse`,
`ProviderFailure`를 사용할 수 있다. 애플리케이션은 그 구분을 보존하되
외부 예외 메시지는 버리고 각각 기존 504/502 오류 계약으로 변환한다.
기존 TimeoutError/HTTPX 타임아웃 및 ValueError 변환도 호환 유지한다.
공급자별 상세 SDK 오류 분류는 각 어댑터 작업에서 보강한다.

문서 원문과 판정은 DTO/result의 repr에 포함하지 않는다.
이는 전체 직렬화·명시적 로깅까지 막는 장치가 아니므로 원문 로깅은 계속 금지한다.
모델 호출의 LangSmith 자동 추적 차단과 별도 안전 메타데이터 기록은 유지한다.
키·환경변수·규정은 이번 작업에서 추가하지 않았다.

## 검증 범위

내부 입력 변환, 불변 컨테이너, 원문 repr 제외, 토큰 유효성,
스텁 포트 준수, 공통 오류 유형 보존 및 단일 호출, 잘못된 결과 유형 거부를 테스트한다.
기존 Gemini SDK 모의 테스트도 내부 포트를 통해 실행한다.
실제 Gemini 요청이나 공급자 추가, 비용 계산, 스트리밍은 이번 작업 범위가 아니다.

2026-10-20 검증: Python 3.11·3.14에서 각각 96개 테스트 통과,
Ruff 검사·포맷 검사 및 mypy 통과. Spring 코드는 변경하지 않았다.
