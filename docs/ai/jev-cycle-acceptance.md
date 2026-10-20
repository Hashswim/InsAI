# 실행 및 검증

## 설정
1. 로컬에서는 back/ai-service의 .env.example을 .env로 복사한다. 기본값은 키 없는 stub 모드다.
2. JEV_SERVICE_TOKEN에 충분히 긴 임의 문자열을 넣는다.
3. 실제 분석은 JEV_PROVIDER=gemini로 변경하고 GOOGLE_API_KEY와 사용할 GEMINI_MODEL을 설정한다.
4. LangSmith 사용 시 LANGSMITH_ENABLED=true, LANGSMITH_API_KEY, LANGSMITH_PROJECT를 설정한다.
   자동 추적 설정은 false로 유지한다. 사용자 지정 endpoint는 LANGSMITH_ENDPOINT로 설정한다.
5. Spring 실행 환경에도 동일한 JEV_SERVICE_TOKEN을 설정한다.
   Spring은 Python 서비스의 .env를 자동으로 읽지 않는다.
   JEV_BASE_URL 기본값은 http://localhost:8085 이다.
6. 실제 PDF에는 의료정보가 포함되므로 초기 검증은 익명화된 파일로 진행한다.

스텁 연결만 확인하려면 모델 키 없이 JEV_PROVIDER=stub을 사용하고,
Spring에도 JEV_ALLOW_STUB=true를 명시한다. 스텁 결과는 실제 문서 판정이 아니다.
APP_ENV는 프로세스 환경에서 지정하며 test/staging/production에서는 .env를 읽지 않는다.
운영 필수 설정과 마운트된 비밀정보 사용법은 [환경설정 안내](configuration-secrets.md)를 따른다.

키는 채팅이나 저장소에 넣지 않는다. .env는 gitignore 대상이다.

## 로컬 실행
프로젝트 루트에서 PostgreSQL/Redis 시작:
```sh
docker compose up -d
```

별도 터미널:
```sh
cd back/ai-service
python3.11 -m venv .venv
.venv/bin/python -m pip install -e '.[test,dev]'
.venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8085 --no-access-log
```

별도 터미널에서 JEV_SERVICE_TOKEN을 환경에 설정한 다음:
```sh
cd back/insai
./gradlew bootRun
```

Spring 시작 시 Flyway V6가 jev_analyses 테이블을 추가한다.
FastAPI 문서는 http://localhost:8085/docs 에서 확인한다.
local/stub에서는 모델 키 없이 시작할 수 있다. 서비스 토큰이 없으면 분석 API는 503이다.
gemini를 선택하고 필수 설정이 없거나 운영 환경 설정이 불완전하면 시작 자체가 실패한다.
운영 조회는 /health/live, /health/ready, /version을 사용한다.
토큰 미설정 시 ready는 503이며, 준비 결과가 200이어도 외부 모델 인증을 확인한 것은 아니다.
자세한 의미는 [운영 엔드포인트 안내](operations-endpoints.md)를 참고한다.

## 실제 한 사이클
1. 기존 로그인/청구 생성 API로 사용자와 케이스를 준비한다.
2. 기존 /files/upload-url → PUT 업로드 → POST /files 로 PDF 세 개를 등록한다.
3. jev-contract.md의 POST /ai/jev 요청에 해당 케이스 ID와 실제 파일 ID를 넣는다.
   JEV 요청과 조회에는 로그인 accessToken을 Authorization: Bearer로 전송한다.
   X-User-Id만으로는 JEV를 호출할 수 없다. 서버 전용 서비스 토큰을 사용자 앱에 넣지 않는다.
4. 응답의 analysisId로 GET /ai/jev/{analysisId}를 호출한다.
5. 저장 후 재조회된 항목 근거/신뢰도/모델/trace ID가 원 응답과 같은지 확인한다.
6. 규정 미등록 때문에 항상 ON_HOLD와 NOT_REGISTERED인지 확인한다.
7. LangSmith에서 trace ID를 찾아 원문/프롬프트/판정이 없는지 확인한다.
8. 다른 케이스의 파일 ID, 중복 파일, 잘못된 PDF가 거부되는지 확인한다.

LangSmith에는 수동 메타데이터 trace를 기록한다. 모델 내부 child trace와 원문 재생 기능은 제공하지 않는다.
실행 시간은 trace 시작/종료로 확인하고 토큰 수는 metadata에서 확인한다.
trace_status=FAILED이면 키/프로젝트/endpoint/네트워크 설정을 확인한다.

## 자동 검증
```sh
cd back/ai-service
.venv/bin/pytest -q
```
```sh
cd back/insai
./gradlew test
```

테스트는 가짜 모델과 합성 입력을 사용하며 외부 API 비용을 발생시키지 않는다.
개발 전체 검사는 `make check`, 실제 설치 패키지의 기본 HTTP 검사는 `make smoke`로 실행한다.
가상환경 경로가 다르면 `make check PYTHON=/가상환경/bin/python`처럼 지정한다.
프로젝트 기반의 검증 결과는 [05번 작업 기록](project-foundation.md)을 참고한다.
HTTP 계약 테스트, 실제 H2 저장/재조회 테스트, PDF 검증 및 근거 참조 검증,
원문을 전송하지 않는 trace 허용 목록 테스트를 포함한다.
실제 Gemini/LangSmith 연결은 사용자 키와 예제 PDF를 설정한 후 별도 검증해야 한다.
PostgreSQL Flyway 실행은 로컬 DB에서 별도 확인한다.

구현 참고:
- https://reference.langchain.com/python/langchain-google-genai/chat_models/ChatGoogleGenerativeAI
- https://reference.langchain.com/python/langsmith/client/Client/create_run
