# 구조화 출력과 파싱 계층

## 책임 경계

`app/chains/structured_output.py`가 공급자 공통 JSON 디코딩과 Findings 검증을 맡는다.
어댑터는 정상 종료·거부·도구 호출 등 공급자 응답 봉투를 확인한 뒤 이 계층을 호출한다.
현재 제공자 결과 재검증은 [J 체인](judgment-chain.md)에서 수행하며,
그 다음 [E 체인](evidence-chain.md)이 실제 요청 PDF의 문서 ID·페이지 범위와 중복 근거를 검사한다.
Spring은 업무 결과를 저장하며 원본 모델 응답이나 파서 예외를 받지 않는다.

작업 19에서는 파싱 기준 강화로 체인을 `jev-chain-v2`로 올렸다. 당시 프롬프트는 유지했다.
작업 20 당시 버전은 프롬프트 jev-v2 / 체인 jev-chain-v3이며 파싱 기준은 유지했다.
작업 21에서 E 체인을 연결한 현재 체인은 jev-chain-v4다. 프롬프트·파싱 기준은 유지한다.
응답 필드 추가나 DB 마이그레이션은 없다. 과거 v1 결과를 v2로 재표기하지 않는다.

## 공통 수용 기준

- UTF-8 기준 Findings JSON 최대 1MiB, 중첩 최대 32단계. JSON 객체 하나만 허용한다.
  크기·중첩을 먼저 검사한다. 문자열 내부의 괄호·이스케이프는 중첩에 포함하지 않는다.
- 모든 깊이의 중복 키를 거절한다. 유니코드 이스케이프로 같은 키를 표현해도 중복이다.
  NaN/Infinity 및 부동소수점 범위를 넘는 숫자도 거절한다.
- 코드블록, 설명문, 주석, 후행 쉼표, 잘린 JSON, 이어 붙인 JSON을 수정·추출하지 않는다.
  JSON 앞뒤의 정상 공백만 허용한다. 모델 재질의·자동 복구는 하지 않는다.
- 최상위는 items만 허용한다. 항목 1–100개, 항목당 근거 최대 20개,
  필수 필드·문자열 길이·enum·UUID·알 수 없는 필드를 검사한다.
- page는 1 이상의 JSON 정수만 허용한다. true, 1.0, "1"은 거절한다.
  confidence는 유한한 JSON 숫자 0–1만 허용한다. 정수 0/1은 허용하지만 bool·숫자 문자열은 거절한다.
- 항목명·근거 설명·검증 사유는 공백뿐일 수 없다. 문자열은 UTF-8로 인코딩 가능해야 한다.
  문자열 내용을 임의로 다듬거나 근거·판정·보완사항을 생성하지 않는다.
- 기존 정책 유지: SUITABLE/NEEDS_SUPPLEMENT에는 근거가 있어야 하고,
  NEEDS_SUPPLEMENT에는 공백 아닌 보완사항이 필요하다.
  UNVERIFIABLE은 유효한 숫자 신뢰도를 받은 뒤 0으로 정규화한다.
- 서버가 다시 검사할 때도 SDK의 구성/수정 가능한 Pydantic 인스턴스를 그대로 신뢰하지 않는다.
  실제 요청에 없는 문서나 PDF 범위를 벗어난 페이지는 실패다.

스키마 검증은 근거 설명의 사실성·문서 진위·의학적 적절성을 입증하지 않는다.
규정은 여전히 미등록이며 최종 제출 상태는 ON_HOLD다. 단계별 J/E/V 체인은 후속 작업이다.

## 공급자별 처리

### Gemini

LangChain의 JSON schema structured output과 include_raw를 유지한다.
STOP 정상 종료, 비차단 입력, 유효한 토큰 사용량, SDK 파싱 성공을 확인한다.
일반/잘못된 도구 호출과 거부 표시를 허용하지 않는다.
raw.content의 문자열 또는 단일 text 블록만 JSON으로 직접 검증한다.
다중·혼합 블록을 합치지 않으며 raw 검증 결과와 SDK parsed 결과가 달라도 거절한다.
SDK가 숫자 문자열을 변환하거나 코드블록·부분 JSON을 파싱했더라도 원본 검증을 통과해야 한다.

실제 LangChain/Google SDK를 사용하되 HTTP 전송만 모의 처리한 테스트로
PDF 입력·정상 결과와 코드블록·중복 키·숫자 문자열·잘린 출력 거절을 검증한다.

### OpenAI

Responses API의 json_schema/strict 요청을 유지한다.
completed, error/incomplete_details 없음, 정확히 하나의 assistant message와
하나의 output_text를 요구한다. reasoning 항목은 반환 Findings로 사용하지 않는다.
refusal·도구 호출·추가 메시지·추가 텍스트 블록은 실패다.
외부 HTTP 응답 봉투도 UTF-8 JSON, 최대 4MiB, 같은 중복 키·중첩·수치 검사를 적용한다.
이 상한은 HTTPX가 응답을 버퍼링한 **이후** 검사이며 네트워크 수신량/메모리의 스트리밍 상한은 아니다.
Gemini도 SDK가 반환한 콘텐츠 이후 검사하므로 SDK 내부 버퍼의 크기까지 제한하지 않는다.

[공식 OpenAI 구조화 출력 문서](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses)는
거부·불완전 응답을 별도로 처리하도록 안내한다. 이번 구현은 이를 성공 Findings로 변환하지 않는다.
지원 가능한 실제 모델은 기존 모델 카탈로그의 명시적 등록 정책을 따른다.

## 오류·추적·비용

파서 오류는 입력·키 이름·JSON 위치·Pydantic 오류 상세를 포함하지 않는 고정 ValueError다.
어댑터/서비스 경계에서 InvalidProviderResponse로 바꾸고 외부에는
HTTP 502 / INVALID_AI_RESPONSE와 요청 ID만 반환한다.
파싱·검증 오류는 일시적 공급자 장애가 아니므로 재시도·fallback 대상이 아니다.
LangSmith는 기존 허용 메타데이터와 ANALYSIS_FAILED만 기록하며 inputs/outputs는 빈 객체다.
모델에 요청한 뒤 실패했으므로 비용 가드가 켜졌다면 기존 전체 예약 상한을 유지한다.
잘못된 응답에서 추정 사용량을 복구해 환급하지 않는다.

환경변수·실제 키·실제 의료 파일은 이번 테스트에 사용하지 않았다.
