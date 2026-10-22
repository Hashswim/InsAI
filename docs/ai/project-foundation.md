# 작업 05 — Python AI 서비스 프로젝트 기반

상태: 기반 보완 및 로컬 검증 완료.

## 구성한 내용

- `.python-version`: Python 3.11을 로컬·Docker의 기준 minor 버전으로 지정.
- `pyproject.toml`: 런타임·test·dev 의존성 분리, Ruff lint/format 및 mypy 설정.
- 앱 함수의 타입 주석 추가, Gemini 구조화 응답의 dict 여부 검사.
- 프롬프트와 비워 둔 규정 파일을 wheel package data에 포함.
- `Makefile`: install/run/check/format/build/smoke 공통 명령.
- `scripts/smoke.py`: 임시 디렉터리에서 HTTP 서버를 시작해 설치된 프롬프트·health·OpenAPI 확인.
- `Dockerfile`: Python 3.11, 일반 사용자로 실행. 비밀정보·개발 캐시를 빌드 컨텍스트에서 제외.
- `.github/workflows/ai-service.yml`: Python 3.11/3.14 검사, 패키지 빌드 및 Docker 실행 검증 설정.
- [서비스 README](../../back/ai-service/README.md): 설치·개발·컨테이너 명령 안내.

기존 프로젝트의 계층은 api/core/schemas/application/jev/providers/observability로 유지했다(초기 이름 `chains/`는 `jev/`로 바뀜).
서비스 설정의 local/test/production 정책과 비밀정보 유효성 검사는 06번에서 보완한다.
이번 단위에서 규정을 작성하거나 모델을 실제 호출하지 않았다.

## 검증 결과

| 검사 | 결과 |
|---|---|
| Python 3.11 신규 임시 가상환경 설치 | test/dev 포함 설치 성공 |
| Python 3.11 `make check` | Ruff·mypy 15개 소스·pytest 10건·포맷 검사 통과 |
| 기존 Python 3.14 `make check` | 동일 검사 통과; Google SDK의 Python 3.17 관련 deprecation 경고 1건 |
| Python 3.11 실제 로컬 HTTP 서버 | health UP, OpenAPI에 JEV 경로 존재 |
| sdist 및 sdist에서 wheel 생성 | 성공 |
| 임시 환경에 wheel 재설치 후 소스 디렉터리 밖에서 실행 | 프롬프트 포함 및 실제 HTTP smoke 통과 |
| 최종 Docker 이미지 빌드 | 성공, 태그 insai-ai-service:task05 |
| 최종 컨테이너 실행 | 외부 네트워크 비활성, 일반 사용자, /tmp 작업 경로에서 HTTP smoke 통과 |

Docker smoke는 호스트 서비스를 노출하지 않고 종료 시 컨테이너를 자동 제거했다.
빌드한 로컬 이미지와 임시 Python 검증 환경은 남겨 두었다.
사용자의 기존 Python 3.14 가상환경은 덮어쓰지 않았다.
GitHub Actions 파일은 작성했지만 원격 CI 실행은 하지 않았다.
모델·LangSmith 실연결과 Spring/PostgreSQL 전체 사이클 검증은 이번 단위의 검사 범위가 아니다.

## 완료 기준 및 한계

키 없이 설치·기본 HTTP 실행·검사·패키징·Docker 실행을 검증했다.
실제 JEV 분석에는 별도 Gemini 설정과 PDF 입력이 필요하다.
의존성에는 호환 버전 범위가 지정되어 있지만 전이 의존성 lock은 없으므로 완전한 재현 빌드가 아니다.
규정 파일은 package data로 전달되지만 현재 분석에서 로딩하지 않는다.
