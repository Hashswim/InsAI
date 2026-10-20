# 작업 07 — 기본 운영 엔드포인트

상태: 구현. 외부 키 입력·실연결은 보류한 채 로컬 조건과 HTTP 동작을 검증한다.
FastAPI :8085에 다음 세 엔드포인트를 제공한다. 모두 서비스 인증 없이 조회 가능하며 비밀값은 반환하지 않는다.

| API | 정상 응답 | 검사 범위 |
|---|---|---|
| GET /health/live | 200, status=UP | HTTP 요청 처리 가능 여부. 외부 모델·DB·LangSmith·서비스 토큰을 검사하지 않음 |
| GET /health/ready | 준비 완료 200, 미완료 503 | 앱 startup 완료, 서비스 토큰 설정, 선택된 공급자의 설정, 번들 JEV 프롬프트 읽기 가능 여부 |
| GET /version | 200, service와 version | 설치된 insai-ai-service 패키지 버전 |

모든 응답에 Cache-Control: no-store를 설정한다.
OpenAPI에는 /health/ready의 200 및 503 응답 스키마를 등록했다.
실제 잘못된 운영 설정은 06번의 시작 검증에서 차단되므로, 그 경우 health 서버도 시작되지 않는다.

## 준비 상태의 의미

예: 서비스 토큰을 설정한 키 없는 stub 모드.

```json
{
  "status": "READY",
  "mode": "stub",
  "checks": {
    "startup": "COMPLETE",
    "service_auth": "CONFIGURED",
    "provider": "STUB",
    "prompt": "AVAILABLE",
    "model_catalog": "COMPATIBLE",
    "routing": "SELECTABLE",
    "langsmith": "DISABLED"
  },
  "external_connections": "NOT_PROBED"
}
```

Gemini를 선택한 경우 provider는 CONFIGURED다. 이는 설정 구조가 유효하다는 뜻이며
실제 키 인증 성공·모델 접근 권한·공급자 연결 성공을 의미하지 않는다.
준비 상태 조회는 공급자 SDK를 초기화하거나 테스트 추론·유료 모델 호출을 수행하지 않는다.
공급자 장애를 능동적으로 탐지하는 기능은 아니며 실제 호출·폴백·관측 작업에서 보강한다.
현재 Gemini/OpenAI/stub의 로컬 설정과 카탈로그 적합성을 검사하며 외부 연결을 추측하지 않는다.
미등록·비활성·요구 기능/한도가 부족한 모델이나 잘못된 카탈로그 파일이면
ready는 503, model_catalog=UNAVAILABLE이다. 상세 모델·파일 경로는 노출하지 않는다.
선택 정책도 같은 함수로 검사해 routing=SELECTABLE/UNAVAILABLE을 반환한다.
실행 가능한 후보가 없으면 ready 503이며 [라우팅 정책](model-routing.md)을 따른다.
17번의 비용 제한을 켠 경우 검토 단가·토큰 상한과 확인된 상한 충돌을 검사해
cost_policy=CONFIGURED/UNAVAILABLE을 반환한다. 꺼져 있으면 DISABLED다.
동적인 잔여 예산·속도 제한은 공개하지 않으며 한도 도달만으로 생존 검사를 실패시키지 않는다.

서비스 토큰이 없으면 live는 200이지만 ready는 503이고 service_auth=MISSING이다.
프롬프트가 없거나 비어 있거나 읽을 수 없으면 ready는 503이고 prompt=UNAVAILABLE이다.
Gemini 실행과 준비 확인이 같은 프롬프트 로더를 사용한다.
종료 시 앱 준비 플래그를 내려 COMPLETE가 남지 않도록 한다.

LangSmith는 선택적 의존성이므로 ready 결과를 차단하지 않는다.
langsmith=CONFIGURED 역시 설정 상태일 뿐 연결 검증 결과가 아니다.
FastAPI는 업무 DB·Redis를 사용하지 않으므로 해당 시스템을 readiness 필수 검사에 넣지 않는다.
프롬프트 원문, 모델 ID, 프로젝트명, 키, 토큰, 내부 예외·파일 경로는 상태 응답에 포함하지 않는다.

## 버전

pyproject.toml의 패키지 버전이 설치 메타데이터에 반영되며, 이를 /version과 FastAPI OpenAPI가 함께 사용한다.
소스만 복사하고 패키지가 설치되지 않은 경우에는 버전을 추측하지 않고 0+unknown을 반환한다.
버전 변경 후에는 패키지를 다시 설치하거나 이미지를 재빌드해야 한다.
키·환경변수·Git 작업 경로·커밋의 민감한 부가정보를 버전에 섞지 않는다.

## 확인 방법

서버가 실행 중일 때:

```sh
curl -i http://127.0.0.1:8085/health/live
curl -i http://127.0.0.1:8085/health/ready
curl -i http://127.0.0.1:8085/version
```

환경변수를 아직 설정하지 않았다면 local/stub의 live와 version을 확인할 수 있다.
ready의 503은 서비스 토큰 미설정에 따른 정상적인 미준비 표시다.
키 입력 보류 상태에서 이를 READY로 만들기 위해 실제 비밀정보를 생성·저장하지 않는다.

## Docker·자동 검사

Docker HEALTHCHECK는 30초 간격, timeout 3초, 시작 유예 10초, 재시도 3회로 /health/live를 호출한다.
현재 컨테이너 내부 포트는 8085다. 이 healthcheck는 생존 검사이며 readiness를 대신하지 않는다.
배포 계층이 준비 여부를 확인할 때는 /health/ready의 HTTP 상태를 사용한다.

make smoke는 테스트 환경의 임시 합성 토큰으로 실제 HTTP live/ready/version과 OpenAPI를 확인한다.
실제 .env나 모델 키는 읽지 않으며 외부 API도 호출하지 않는다.
테스트 fixture는 임시 작업 디렉터리에서 실행돼 local 모드 검사도 개발자의 실제 .env를 읽지 않는다.
정상·미준비·프롬프트 장애·종료 플래그·버전 일치·외부 SDK 미호출을 자동 테스트로 확인한다.
환경 설정은 [06번 안내](configuration-secrets.md)를 따른다.

검증 결과: Python 3.11·3.14에서 테스트 46건, lint·타입 검사(19개 소스)·포맷 검사 통과.
최종 Docker 이미지의 실제 HTTP live/ready/version smoke를 통과했다.
별도 임시 컨테이너에서는 키·서비스 토큰 없이 Docker 생존 검사가 healthy, 실패 연속 횟수 0임을 확인했다.
생존 검사는 준비 여부와 별개이며, 시험 후 임시 컨테이너를 종료·제거했다.
사용자의 .env나 실제 키는 생성·입력·변경하지 않았다. 실제 공급자 연결은 미검증이다.
