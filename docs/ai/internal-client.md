# 작업 25 — Spring ↔ FastAPI 내부 클라이언트

상태: 구현·모의 HTTP 검증 완료. 실제 Gemini·LangSmith·운영 네트워크 연결 검증은 아니다.
환경변수 입력은 보류하며 프론트·규정 파일은 변경하지 않는다.

## 호출과 책임

Spring의 `RunJevService`가 사용자·케이스·파일 권한을 확인하고 세 PDF를 조립한다.
`FastApiJevClient`는 `POST /internal/jev`에 서비스 Bearer 토큰,
동일한 `X-Request-ID`와 `request_id`, JSON 본문을 보낸다.
사용자 JWT·Gemini 키는 이 RPC에 전달하지 않는다. 토큰이 없으면 네트워크 호출 전에 실패한다.
주소는 신뢰된 서버 설정이며 사용자 요청으로 변경할 수 없다.
운영에서는 HTTPS 또는 별도로 보호된 내부 전송망과 접근 제어가 필요하다.

클라이언트의 애플리케이션 수준 POST 재시도·폴백은 없다. 공급자 재시도는 FastAPI가 소유한다.
리다이렉트는 명시적으로 금지하여 PDF·토큰이 다른 주소로 따라가지 않게 한다.
타임아웃 후에도 원격 모델 실행이 취소됐다는 보장은 없으므로 임의 재호출하지 않는다.
26번 [영구 job·중복 접수 방지](async-jobs.md)를 추가했다. 동기 API와 원격 호출의 정확히 한 번 실행까지 보장하지는 않는다.

## 전송 수용 조건

- HTTP 200만 성공 후보로 처리한다. 201·202·3xx 등은 성공으로 저장하지 않는다.
- 단일 `application/json`, UTF-8(또는 charset 생략), 무압축/identity 응답만 허용한다.
- Content-Length와 실제 읽기 모두 2MiB 상한을 적용한다. 청크 응답도 동일하다.
  FastAPI의 1MiB findings 제한에 결과 메타데이터를 더한 정상 응답을 수용하는 여유 상한이다.
- 상태·헤더·크기 실패 시 본문을 파싱하거나 로그로 남기지 않고 스트림을 직접 닫는다.
  Spring 응답 정리 과정의 잔여 본문 drain도 피한다.
- 연결 제한은 5초, 전송/응답 읽기 제한은 기존 `insai.jev.timeout-seconds`(기본 120초, 1~300초)다.
  설치된 Spring 6.2.19의 스트림 종료 타이머 동작을 소스로 확인했고,
  헤더 도착 전 지연과 헤더 이후 본문 지연을 각각 실제 HTTP 테스트로 검증했다.
  스트림 종료가 일반 IOException으로 전달될 때는 설정 시간이 경과했으면 AI_TIMEOUT으로 분류한다.
  따라서 제한 시점과 겹친 다른 I/O 오류도 타임아웃으로 분류될 수 있다.
  JSON 검증·DB 저장·파일 읽기까지 포함하는 전체 업무 실행 deadline은 아니다.

설정과 타임아웃 API는 [Spring 공식 문서](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/client/JdkClientHttpRequestFactory.html)를 참고한다.

## 라이브 응답 검증

`JevWireResponseDecoder`는 DB 조회용 ObjectMapper와 분리한다.
중복 키, 후행 JSON, 32단계 초과 중첩, 비 UTF-8, 미정의/누락 필드,
문자열↔숫자 암묵 변환과 정수의 소수 변환을 거절한다.

현재 계약의 ON_HOLD/NOT_REGISTERED, request_id 일치, 공급자 허용 목록,
모델·프롬프트·체인 식별자, SHA-256, 검증 한계 메타데이터를 확인한다.
trace ID는 RECORDED일 때만 UUID로 존재해야 하고 DISABLED/FAILED일 때는 명시적 null이어야 한다.
토큰 수는 Java 저장 DTO에 맞는 0~2,147,483,647 정수다.

항목 1~100개, 항목명 중복, 판정 열거형, 유한한 0~1 신뢰도,
UNVERIFIABLE의 신뢰도 0, 판정에 필요한 근거·보완사항을 확인한다.
항목/모델/버전/설명 길이, 잘못된 Unicode surrogate, 근거 20개 상한과 중복도 검사한다.
근거 문서 ID는 반드시 해당 요청의 PDF ID여야 하며 페이지는 1~30 정수여야 한다.
실제 PDF 페이지 수에 대한 더 좁은 검증은 FastAPI E 단계가 담당한다.
의학적 사실·규정 준수·모델 확신도의 보정 정확성을 이 클라이언트가 검증하는 것은 아니다.
stub는 기존 명시적 opt-in일 때만 허용하고 UNVERIFIABLE/신뢰도 0/빈 근거만 수용한다.

## 저장·오류·개인정보

검증된 결과만 반환하고, `RunJevService`는 반환 이후에만 분석 JSON을 저장한다.
실패는 `AI_INVALID_RESPONSE` 또는 [상태별 기존 오류 코드](api-contract-errors.md)로 변환한다.
원격 오류 본문·Jackson/HTTP 예외 원문과 cause는 사용자 예외에 붙이지 않는다.
요청/PDF/결과/항목/근거 DTO의 toString도 고정 REDACTED 문구로 바꿨다.
이는 임의 JSON 직렬화·외부 프록시·별도 HTTP wire logging까지 차단하는 기능은 아니다.
업무 DB에는 권한을 확인한 분석 결과가 저장되지만 LangSmith 원문 저장 금지 원칙은 유지한다.

기존 저장 JSON은 이전처럼 역직렬화한다. 새 메타데이터가 없으면 null로 읽고
현재 버전·검증 상태를 소급해서 만들어 넣지 않는다. 라이브 RPC의 엄격한 수용 조건과 다르다.
공개 요청 필드/봉투와 현재 체인 버전 jev-chain-v7은 변경하지 않았다.

## 검증

공유 계약의 정상 RPC, Gemini/OpenAI 및 opt-in stub, 상태 매핑, 비밀값 비노출,
잘못된 중첩 응답·근거 범위·trace·숫자·JSON, 과대/청크 응답, 잘못된 MIME/인코딩,
리다이렉트 미추종·재시도 없음, 헤더/본문 타임아웃, 오류 시 저장소 미호출을 테스트한다.
기존 DB 저장/조회·접근 권한 통합 테스트도 전체 Spring 테스트에 포함한다.
테스트는 합성 데이터와 로컬 모의 서버만 사용하며 실제 의료 PDF·API 키를 사용하지 않는다.
