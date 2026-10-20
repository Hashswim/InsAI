# InsuranceAI AI service

Python 3.11 기준의 FastAPI 서비스. Gemini로 의료 PDF 세 개를 JEV 분석한다.
규정은 비워 두며 최종 제출 상태는 ON_HOLD다.

## 개발 환경

권장 런타임은 `.python-version`과 Dockerfile의 Python 3.11이다.
기존 가상환경이 다른 Python으로 생성됐다면 덮어쓰지 말고 새 경로에 생성한다.

```sh
python3.11 -m venv .venv
.venv/bin/python -m pip install -e '.[test,dev]'
make check
make run
```

Makefile의 PYTHON을 지정하면 별도 가상환경도 사용할 수 있다.
기본값은 local/stub이며 모델 키 없이 시작·health 확인이 가능하다.
서비스 토큰을 설정하면 실제 모델 호출 없는 스텁 분석도 가능하다.
Gemini를 선택하면 필수 설정이 없을 때 시작 시 실패한다.
설정 변수는 `.env.example`, 전체 서비스 설정·실행은
[실행 안내](../../docs/ai/jev-cycle-acceptance.md),
요청·응답은 [API 계약](../../docs/ai/jev-contract.md)을 참고한다.
환경 선택·키 주입·마스킹·운영 설정은 [환경설정 안내](../../docs/ai/configuration-secrets.md)를 따른다.

## 구성

| 경로 | 역할 |
|---|---|
| app/api | FastAPI 라우트 |
| app/core | 설정·내부 인증·오류 |
| app/schemas | 입력·출력 스키마 |
| app/application | 분석 실행과 응답 조립 |
| app/chains | JEV 워크플로·프롬프트 |
| app/providers | 공급자 인터페이스·라우팅·Gemini 어댑터 |
| app/observability | 원문 없는 LangSmith 추적 |
| app/rules/medical.yaml | 비워 둔 규정 자리; 현재 로딩하지 않음 |
| tests/unit, tests/integration | 가짜 모델 기반 테스트 |

## 개발 검사

`make check`는 Ruff lint, mypy 타입 검사, pytest, 포맷 검사를 수행한다.
`make format`은 포맷을 적용하고 `make build`는 wheel/sdist를 만든다.
`make smoke`는 설치된 패키지의 프롬프트와 실제 HTTP 서버의 health·OpenAPI를 확인한다.
이 검사는 별도 임시 작업 디렉터리에서 서버를 시작해 소스 폴더 의존을 확인한다.
GitHub Actions 설정은 Python 3.11/3.14 검사와 런타임 이미지 검증을 포함한다.
검사 설정은 pyproject.toml에서 관리한다.
Ruff는 [공식 설정 문서](https://docs.astral.sh/ruff/configuration/),
mypy는 [공식 설정 문서](https://mypy.readthedocs.io/en/stable/config_file.html)를 참고한다.
테스트에는 외부 모델 호출이나 실제 환자 문서를 사용하지 않는다.
의존성 범위는 pyproject.toml에 지정하며, 현재 전체 전이 의존성을 고정한 lock 파일은 없다.

## 컨테이너

Docker daemon이 실행 중일 때 이 디렉터리에서:

```sh
docker build -t insai-ai-service:local .
docker run --rm -p 127.0.0.1:8085:8085 --env-file .env insai-ai-service:local
```

이미지에는 개발 도구·테스트·.env를 넣지 않는다. .env 파일은 로컬에서 직접 준비한다.
프로젝트 외부 규정은 번들에 넣지 않으며 프롬프트는 wheel의 package data에 포함한다.
Docker는 동일한 pyproject.toml로 설치한다. 빌드 시점의 의존성 해석은 lock이 아니므로 달라질 수 있다.
