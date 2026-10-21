# 작업 08 — 공통 API 계약 및 오류 규격

상태: 구현 및 계약 테스트. 범위는 JEV 공개 API와 Spring–FastAPI 내부 API다.
기존 프론트가 사용하는 Spring 성공/실패 봉투를 유지하며, 내부 API는 전송 DTO를 직접 반환한다.
규정은 미등록이며 최종 제출 상태는 계속 ON_HOLD다. 환경변수 입력·실제 키 사용은 보류한다.

## API와 식별자

| API | 입력 | 성공 |
|---|---|---|
| Spring POST /ai/jev | caseId, documents[{fileId, kind}], 사용자 Bearer JWT | 200, 기존 봉투의 data={analysisId,result} |
| Spring GET /ai/jev/{analysisId} | 저장된 분석 ID, 사용자 Bearer JWT | 200, 권한 확인 후 동일 형태의 data |
| FastAPI POST /internal/jev | request_id, documents[{document_id,kind,content_base64}], Bearer 토큰 | 200, JevResponse 직접 반환 |

JEV 요청은 Content-Type: application/json을 사용한다.
09번부터 공개 JEV API는 JWT 필수이며 X-User-Id를 신뢰하지 않는다.
인증은 입력 본문 처리보다 우선하므로 비인증 요청은 본문 형식 오류보다 401을 먼저 받을 수 있다.
Spring 업무 필드는 camelCase, 내부 DTO와 result의 모델 결과 필드는 snake_case다.
전체 성공 결과의 합성 예제는 [공유 계약 fixture](contracts/jev-response-example.json)에 있다.
Python 스키마 검증과 Java DTO 역직렬화·재직렬화가 동일 파일을 검증한다.
예제는 실제 모델 결과나 환자 데이터가 아니다.

| ID | 의미 |
|---|---|
| X-Request-ID / request_id | 한 HTTP 분석 실행의 상관관계 UUID |
| caseId | Spring 업무 청구 건 식별자 |
| fileId / document_id | 입력 원본 파일과 근거 연결 UUID |
| analysisId | 성공적으로 저장된 분석의 조회 UUID |
| trace_id | LangSmith 기록 성공 시의 별도 UUID |

26번에서 [비동기 작업 API](async-jobs.md)를 추가했다. POST /ai/jev/jobs는 사용자별 UUID
Idempotency-Key로 중복 접수를 막고 202와 jobId를 반환하며 GET /ai/jev/jobs/{jobId}로 상태를 조회한다.
동기 POST /ai/jev는 기존 동작을 유지한다.
request_id는 인증 정보나 중복 실행 방지 키가 아니다. 같은 ID로 재요청해도 재실행될 수 있다.

## 요청 ID 전달 규칙

1. Spring JEV API에 X-Request-ID가 없으면 UUID를 생성한다.
2. 있으면 표준 하이픈 포함 UUID인지 검사하고 소문자로 정규화한다. 잘못된 값은 400이다.
3. Spring이 같은 값을 내부 요청 헤더와 request_id 본문에 넣는다.
4. FastAPI는 두 값이 다르면 422 REQUEST_ID_MISMATCH로 거부한다.
5. 내부 헤더 없이 직접 호출하면 검증된 본문의 request_id를 사용한다.
6. 본문 검증·인증 이전 실패처럼 본문 ID를 신뢰할 수 없으면 유효한 헤더 ID 또는 새 UUID를 사용한다.
7. 성공·실패 응답의 X-Request-ID에 해당 요청의 값을 반환한다.

FastAPI 오류 본문의 request_id는 응답 헤더와 동일하다.
공개 Spring 오류 본문은 기존 봉투를 유지하므로 상관관계 ID는 응답 헤더로 확인한다.
결과 재조회 GET의 헤더 ID는 해당 GET 요청 ID이며 저장된 분석의 request_id와 다를 수 있다.
Spring ID 필터는 JEV 경로에만 적용하며 다른 업무 API의 응답 계약을 바꾸지 않는다.
사용자 제공의 잘못된 요청 ID나 예외 원문은 응답에 반복하지 않는다.

## 입력·출력 검증

- 입력 문서 종류: DIAGNOSIS, TREATMENT_CONFIRMATION, ITEMIZED_BILL 각 한 개, ID 중복 금지.
- PDF당 10MiB 이하, 1~30페이지, 암호화 금지. base64는 data URL 접두사 없는 PDF 바이트다.
- base64 문자열당 최대 14,000,000자, 내부 HTTP 본문은 43,000,000바이트 이하.
- [입력 방어 정책](input-defense.md)에 따라 중복 JSON/헤더·비 UTF-8·압축 본문과
  지원하지 않는 PDF 활성 기능을 거절한다. 비 JEV 경로의 본문은 64KiB로 제한한다.
- Content-Length뿐 아니라 실제 수신 바이트도 제한한다. JSON 구조·PDF 형식 실패는 422다.
- Spring은 필수 UUID·종류·3개 문서 목록과 null 요소를 검증한다. 업무 권한·파일 소속도 확인한다.
- FastAPI는 정의하지 않은 JSON 필드를 거부한다. Spring의 모든 미정의 필드 거부까지 보장하지는 않는다.
- 25번부터 Spring 내부 RPC의 성공 응답은 미정의·누락·중복 필드와 암묵 숫자 변환도 거부한다.
  [내부 클라이언트 정책](internal-client.md)의 2MiB 전송 상한·근거 범위·검증 메타데이터를 만족해야 저장한다.
  공개 Spring 입력 전체나 기존 저장 결과에 이 엄격 파서를 소급 적용하는 것은 아니다.
- 항목 판정은 SUITABLE / NEEDS_SUPPLEMENT / UNVERIFIABLE만 허용한다.
- 결과 항목 1~100개, 토큰 수는 0 이상, 모델·프롬프트 버전은 비어 있을 수 없다.
- 규정 미등록 응답은 ON_HOLD / NOT_REGISTERED다.
- trace_status는 RECORDED / DISABLED / FAILED다. RECORDED일 때만 trace_id가 있어야 한다.
- 판정별 근거·신뢰도 조건은 [JEV 명세](jev-model-spec.md)를 따른다.

## FastAPI 오류 봉투

```json
{
  "success": false,
  "request_id": "11111111-1111-4111-8111-111111111111",
  "error": {
    "code": "INVALID_INPUT",
    "message": "Invalid JEV request"
  }
}
```

오류 메시지는 서버의 고정 문구다. 원본 PDF·base64·입력 필드 값·SDK 예외·키·내부 경로를 넣지 않는다.
오류에는 Cache-Control: no-store를 설정한다. 401에는 WWW-Authenticate: Bearer를 포함한다.

| HTTP | code | 의미 |
|---|---|---|
| 400 | INVALID_REQUEST_ID | 헤더 ID 형식 오류 |
| 401 | UNAUTHORIZED | 내부 Bearer 인증 실패 |
| 403 | FORBIDDEN | 내부 접근 거부 |
| 404 | NOT_FOUND | 없는 경로 |
| 405 | METHOD_NOT_ALLOWED | 지원하지 않는 메서드 |
| 413 | PAYLOAD_TOO_LARGE | 선언 또는 실제 본문 크기 제한 초과 |
| 415 | UNSUPPORTED_MEDIA_TYPE | 내부 분석 요청이 application/json이 아님 |
| 422 | INVALID_INPUT | JSON·스키마·PDF 입력 검증 실패 |
| 422 | REQUEST_ID_MISMATCH | 헤더와 검증된 본문 ID 불일치 |
| 429 | RATE_LIMITED | 17번의 요청·호출·동시 처리·추정 비용 한도 초과 |
| 500 | INTERNAL_ERROR | 분류되지 않은 서버 오류 |
| 502 | AI_ANALYSIS_FAILED | 모델 분석 실패 |
| 502 | INVALID_AI_RESPONSE | 출력 형식·근거 검증 또는 API 응답 스키마 실패 |
| 503 | SERVICE_UNAVAILABLE | 내부 인증 미설정·공급자 초기화 실패 등 |
| 504 | AI_TIMEOUT | 인식 가능한 모델 타임아웃 |

내장 TimeoutError와 HTTPX TimeoutException을 분류하며 공급자별 모든 오류 코드 해석은 후속 어댑터 작업이다.
운영 readiness의 503은 실패 예외가 아니라 미준비 결과이므로 기존 Readiness 본문을 유지한다.

## Spring 오류 변환

```json
{"success": false, "data": null, "error": {"code": "AI_TIMEOUT", "message": "AI 분석 응답 시간이 초과되었습니다."}}
```

Spring 클라이언트는 내부 오류 본문을 사용자에게 복사하지 않고 HTTP 상태를 기준으로 변환한다.
내부 인증 실패를 사용자의 로그인 실패로 오인하지 않도록 구분한다.

| 내부 상태·조건 | 공개 Spring 상태 / code |
|---|---|
| 400, 415, 422 | 400 / INVALID_INPUT |
| 413 | 413 / PAYLOAD_TOO_LARGE |
| 401, 403, 503 | 503 / AI_SERVICE_UNAVAILABLE |
| 429 | 429 / AI_RATE_LIMITED |
| 504, 실제 HTTP 읽기 타임아웃 | 504 / AI_TIMEOUT |
| 그 외 오류 HTTP(예: 500, 502) | 502 / AI_UPSTREAM_ERROR |
| 성공 HTTP의 잘못된 JSON·필수 응답 상태·요청 ID 불일치 | 502 / AI_INVALID_RESPONSE |
| 접속 실패·서비스 토큰 미설정 | 503 / AI_SERVICE_UNAVAILABLE |

공개 입력의 잘못된 X-Request-ID는 400 / INVALID_REQUEST_ID다.
공개 요청의 미지원 형식·메서드는 각각 415 / UNSUPPORTED_MEDIA_TYPE, 405 / METHOD_NOT_ALLOWED다.
Spring의 일반 예외 처리도 미지원 형식·메서드·없는 리소스를 기존 봉투로 반환하도록 보완했다.
업무 권한 403, 없는 업무 데이터 404, DB 저장 오류 500 등 기존 업무 규칙은 유지한다.
상태 변환은 자동 재시도를 허용한다는 뜻이 아니다. 큐·재시도·중복 실행 방지는 별도 작업이다.

## OpenAPI와 버전 정책

FastAPI /docs 및 /openapi.json에 Bearer 인증, X-Request-ID, 요청·성공·오류 스키마를 제공한다.
유료 추론이나 실제 키 없이 OpenAPI를 조회·검증할 수 있다.
현재 계약은 v1이며 기존 /internal/jev 경로를 유지한다. enum·필수 필드·상태 의미를 깨는 변경은
계약 버전을 새로 정하고 양쪽 서비스를 함께 검증한다. 버전 번호만으로 호환성을 추정하지 않는다.
추가 모델 공급자를 넣을 때 Python provider enum과 Java 클라이언트 허용 범위도 함께 변경해야 한다.

## 검증 범위

정상·누락/불일치 ID, JSON·형식·본문 크기, 인증, 404·405, 공급자/출력/타임아웃 오류,
예외 원문 비노출, OpenAPI 실제 스키마와 공유 성공 fixture를 검사한다.
Java에서는 공개 오류 봉투·ID 전달·11개 내부 HTTP 오류 매핑·잘못된 성공 JSON·실제 읽기 타임아웃을 검사한다.
환경변수·실제 키를 입력하지 않았으며 외부 모델을 호출하지 않는다.

검증 결과: Python 3.11·3.14의 테스트 74건, lint·타입 검사(21개 소스)·포맷 검사 통과.
Spring 전체 테스트 통과. 최종 Docker 이미지의 실제 HTTP 운영 API와
404 오류 봉투·응답 헤더/본문 요청 ID 일치를 smoke 검사로 확인했다.
