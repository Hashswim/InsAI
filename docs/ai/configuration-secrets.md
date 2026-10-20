# 작업 06 — 환경설정 및 비밀정보 관리

상태: 구현 및 자동 검증 완료. 규정 내용과 실제 API 키는 추가하지 않았다.

## 환경 선택

APP_ENV는 프로세스 환경에서 선택한다. 로컬 .env의 APP_ENV 항목만 바꿔 운영 모드를 선택할 수 없다.
환경이 잘못되거나 선택한 모드의 필수 설정이 부족하면 FastAPI lifespan에서 시작을 중단한다.

| 환경 | .env 로딩 | 모델 모드 | 필수 설정 |
|---|---|---|---|
| local | 현재 실행 디렉터리의 .env를 선택적으로 읽음 | 기본 stub; 명시적으로 gemini/openai 선택 가능 | stub의 health는 키 없이 가능. 분석 API에는 서비스 토큰 필요 |
| test | 읽지 않음 | stub만 허용, LangSmith 비활성 | 외부 호출 없이 테스트. 테스트 fixture가 설정·캐시·의존성 override를 격리 |
| staging | 읽지 않음 | gemini 필수 | 서비스 토큰 32자 이상, Google 키, 모델 ID |
| production | 읽지 않음 | gemini 필수 | staging과 동일 |

로컬 실행의 기본값은 local/stub다. Docker 이미지 기본값은 production/gemini여서
설정 없이 배포하면 시작이 실패한다. Docker에서도 개발용으로 쓰려면 환경을 명시적으로 재정의한다.
기존 `.env.example`을 Docker `--env-file`로 전달하면 그 내용은 프로세스 환경으로 전달된다.
운영 환경이 `.env`를 자체 로딩하지 않는다는 규칙과 구분한다.

## 설정 항목

| 변수 | 기본값·처리 |
|---|---|
| APP_ENV | local; local/test/staging/production만 허용 |
| JEV_PROVIDER | stub; stub/gemini/openai 허용. staging/production은 gemini만 허용 |
| JEV_SERVICE_TOKEN | 빈 값. Gemini/OpenAI 모드는 필수; staging/production은 32자 이상. 공백 없는 ASCII |
| GOOGLE_API_KEY | 빈 비밀값. gemini 선택 시 필수 |
| GEMINI_MODEL | 빈 값. gemini 선택 시 계정에서 사용 가능한 모델 ID 필요 |
| OPENAI_API_KEY | 빈 비밀값. openai 선택 시 필수. 실제 키는 아직 입력하지 않음 |
| OPENAI_MODEL | 빈 값. openai 선택 시 PDF·구조화 출력 지원 모델 ID 필요 |
| LANGSMITH_ENABLED | false. true이면 LANGSMITH_API_KEY 필수 |
| LANGSMITH_API_KEY | 빈 비밀값. 활성화된 경우에만 SDK 인증에 사용 |
| LANGSMITH_PROJECT | insai-jev |
| LANGSMITH_ENDPOINT | https://api.smith.langchain.com; 자격증명·query·fragment 없는 HTTPS URL |
| JEV_SECRETS_DIR | 선택적 마운트 디렉터리. 프로세스 환경에서 지정; 없는 경로는 시작 실패 |
| LANGSMITH_TRACING / LANGCHAIN_TRACING_V2 | 앱이 자동 추적을 false로 강제. LangSmith 수동 허용 목록만 사용 |
| PYDANTIC_SETTINGS_DEBUG | 활성화 금지. 설정 원본을 출력할 수 있어 시작 전에 거부 |

개발·테스트에서만 LangSmith endpoint의 HTTP localhost/127.0.0.1/::1을 허용한다.
키에 공백·개행이 있으면 거부한다. 공급자 키의 실제 유효성이나 모델 이용 권한은 시작 시 외부 호출로 검사하지 않는다.
장애·요금 없이 설정 구조만 검증하며 실제 인증은 실연결 단계에서 확인한다.

## 비밀정보 주입과 교체

애플리케이션 설정 경계는 Pydantic Settings로 통합했다.
런타임 우선순위는 환경변수 → 로컬에서만 .env → 마운트된 비밀 파일 → 기본값이다.
APP_ENV 선택과 JEV_SECRETS_DIR 경로는 프로세스 환경만 사용한다.
같은 변수의 빈 환경값도 하위 비밀 파일을 덮어쓰므로, 마운트를 쓸 때는 해당 빈 환경변수도 제거한다.

마운트 디렉터리는 다음 파일명으로 비밀값만 제공한다.

```text
/run/secrets/insai/
  JEV_SERVICE_TOKEN
  GOOGLE_API_KEY
  LANGSMITH_API_KEY
```

배포 환경의 비밀정보 관리 시스템이 환경변수 또는 읽기 전용 파일로 제공하면 앱을 변경하지 않고 쓸 수 있다.
AWS Secrets Manager를 직접 호출하는 SDK·IAM 연동은 이번에 추가하지 않았다.
AWS를 선택할 때도 해당 배포 환경의 주입 계층을 통해 같은 설정 경계를 사용할 수 있다.
Spring은 Python의 JEV_SECRETS_DIR을 읽지 않으므로 서비스 토큰을 Spring 환경에도 별도로 주입한다.

로컬 키는 추적되지 않는 .env에 직접 입력하고 파일 접근 권한을 소유자로 제한한다.
키를 소스·테스트 fixture·Dockerfile·이미지 빌드 인자·채팅에 넣지 않는다.
변경 후 FastAPI를 재시작한다. 설정과 공급자 인스턴스는 캐시되며 실행 중 자동 교체하지 않는다.
공유 서비스 토큰은 두 서비스를 함께 갱신한다. 무중단 이중 토큰 교체는 아직 제공하지 않는다.

## 마스킹과 오류

- Python의 비밀 필드는 SecretStr와 repr 제외로 설정 출력·JSON 직렬화에서 숨긴다.
- SDK/인증 비교 경계에서만 명시적으로 원래 값을 꺼내 사용한다.
- 시작 설정 오류는 입력값을 포함하지 않는 ConfigurationError로 바꾼다.
- 표준 Python 로그는 현재 설정된 키·토큰과 URL 인코딩 값을 메시지·포맷 인자·예외 traceback에서 치환한다.
- 마스킹은 앱 lifespan 동안 설치하고 종료 시 원래 LogRecordFactory로 복원한다.
- Spring의 JEV 설정 및 기존 AiProperties의 toString에서도 토큰·키를 숨긴다.
- JEV endpoint URL에 사용자명·비밀번호·query·fragment를 넣을 수 없으며 타임아웃은 1~300초로 검증한다.

로그 마스킹은 임의의 print, 사용자 지정 JSON 로그의 extra 필드, 외부 프록시 로그까지 보장하지 않는다.
의료 원문과 모델 응답의 일반 로그 기록 금지 정책은 별도로 유지하며, 전체 SDK·예외 경로는 24번에서 보강한다.
설정 객체의 원시 dump나 환경 전체 dump를 디버깅 목적으로 출력하지 않는다.

## 키 없는 스텁 연결

로컬 .env에서 JEV_PROVIDER=stub, LANGSMITH_ENABLED=false로 두고 서비스 토큰만 설정한다.
동일 토큰을 Spring에 제공하고 JEV_ALLOW_STUB=true를 명시한다.
실제 PDF 입력 검증은 수행하지만 문서 내용은 분석하지 않으며 세 항목 모두 UNVERIFIABLE·신뢰도 0이다.
provider=stub, model=stub-v1이며 Gemini로 자동 전환하거나 원격 호출하지 않는다.
Spring의 기본 JEV_ALLOW_STUB=false는 스텁 응답을 거부한다.
실모델 검증 시 JEV_PROVIDER=gemini와 실제 키·모델을 설정하고 JEV_ALLOW_STUB는 false로 돌린다.
FastAPI staging/production은 stub 자체를 거부한다. Spring의 전체 운영 인증·배포 구성 완료를 뜻하지 않는다.

## 검증

설정 우선순위, 로컬 dotenv와 테스트·운영 격리, 마운트 파일,
필수 설정 누락, 운영 토큰 길이, endpoint 제한, 설정·예외·표준 로그의 키 마스킹,
스텁 실제 라우트의 외부 SDK 미사용을 자동 테스트로 확인한다.
Spring은 설정 출력 마스킹·잘못된 URL/타임아웃·스텁 명시적 허용과 기존 테스트를 검증한다.
실제 Google/LangSmith 키나 AWS 계정은 사용하지 않았다.

검증 결과: Python 3.11·3.14에서 lint·mypy(17개 소스)·포맷 검사와 pytest 38건 통과.
Spring 전체 테스트 통과. 최종 Docker 이미지에서 키 없는 테스트 모드의 실제 HTTP 실행과
운영 기본 모드의 자격증명 누락 시 서버 시작 거부를 확인했다.
실제 공급자 키의 인증 성공과 비밀정보 관리 서비스 실연결은 아직 검증하지 않았다.

설정 소스의 공식 동작은 [Pydantic Settings 문서](https://github.com/pydantic/pydantic-settings/blob/main/docs/index.md)를 참고한다.
