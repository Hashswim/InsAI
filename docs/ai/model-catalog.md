# 작업 14 — 모델 카탈로그와 Capability Registry

상태: 서버 측 선언형 모델 카탈로그와 JEV 호환성 검사 구현.
Gemini·OpenAI 실모델 ID는 아직 선택하지 않아 기본 실모델 목록은 비어 있다.
13번은 사용자 요청으로 도입 보류했으며 관련 실행 코드·전용 의존성은 원래 없었다.
AWS의 다른 기능이나 기존 계정 설정은 변경하지 않았다.

## 등록 내용

`app/providers/catalog.py`의 불변 ModelProfile과 ModelCatalog가 다음을 관리한다.

| 항목 | 의미 |
|---|---|
| provider + model | 정확히 일치해야 하는 공급자·모델 ID. 별칭·접두사 추측 없음 |
| enabled | 관리자 활성/비활성 선언 |
| capabilities | pdf_input, structured_output, token_usage 지원 선언 |
| max_pdf_documents | 한 요청의 지원 PDF 개수 |
| max_output_tokens | 선언된 모델 최대 출력 토큰 |
| context_window_tokens | 선택적 컨텍스트 크기. null은 미확인이지 무제한이 아님 |
| source | 관리자 검토 근거. 공급자 문서·검토 기록 등. 비밀값/의료 원문 금지 |

실제 모델의 기능을 API로 조회하거나 이름에서 추론하지 않는다.
등록은 관리자의 지원 기능 선언이며 계정 권한이나 실모델 검증 완료를 뜻하지 않는다.
요금은 추정해 채우지 않았다. 가격/비용 정책은 17번에서 다룬다.

기본 stub-v1은 별도 내장 항목이다. PDF 입력·구조화 응답의 연결 확인만 지원하며
내용을 해석하는 모델이 아니다. 일반 실모델 후보 목록에서 제외하고 명시적 스텁 모드에서만 사용한다.
외부 파일로 내장 스텁을 교체할 수 없다.

## 서버 측 파일

JEV_MODEL_CATALOG_PATH를 지정하지 않으면 내장 스텁만 등록된다.
`config/model-catalog.example.json`은 다음의 빈 형식 예제이며 실제 모델을 임의로 채우지 않았다.

```json
{"schema_version": 1, "models": []}
```

실모델을 선택한 뒤 해당 파일의 models에 아래 형식으로 등록한다.
아래 모델 ID·수치는 형식 설명용이며 실제 지원을 주장하는 등록 예제가 아니다.

```json
{
  "provider": "gemini",
  "model": "YOUR_VERIFIED_MODEL_ID",
  "enabled": true,
  "capabilities": ["pdf_input", "structured_output", "token_usage"],
  "max_pdf_documents": 3,
  "max_output_tokens": 16000,
  "context_window_tokens": null,
  "source": "실제 공급자 문서와 검토 기록으로 교체"
}
```

공급자는 gemini/openai만 외부 등록 가능하다. 동일 공급자·모델 중복,
알 수 없는 필드·기능·스키마 버전, 중복 JSON 키, 잘못된 자료형, 1MiB 초과 파일은 거부한다.
모델명에 와일드카드를 넣어 포괄 허용할 수 없다. 환경·키 정책은 등록으로 우회할 수 없으며
staging/production은 계속 Gemini만 허용한다.
절대 파일 경로 사용을 권장한다. 컨테이너에서는 이 파일을 별도 읽기 전용 마운트하고 경로를 주입한다.
예제는 외부 운영 설정을 위한 문서 자산이며 실제 모델 등록 파일이나 비밀값을 생성한 것은 아니다.

## 실행 경계

기존 선택된 JEV_PROVIDER 및 모델 ID를 등록 목록에서 정확히 찾는다.
JEV에는 pdf_input·structured_output, PDF 3개 이상과 최대 출력 16,000토큰 이상 지원이 필요하다.
두 어댑터의 출력 상한도 같은 상수를 사용한다. token_usage는 선택적이며 미제공 시 기존처럼 0이다.
등록되지 않았거나 비활성·미지원인 모델은 build_provider에서 SDK 초기화 전에 차단한다.
외부 카탈로그 없이 Gemini/OpenAI를 선택하면 이제 호출 API는 503으로 거부한다.
실제 연결 시에는 기존 키·모델 설정에 더해 해당 등록이 필요하다.

readiness는 같은 로더·호환성 검사를 수행하고 checks.model_catalog를
COMPATIBLE 또는 UNAVAILABLE로 반환한다. 미준비는 503이지만 live는 계속 200이다.
오류 응답에는 파일 경로·모델 ID·파일 원문·검증 예외를 노출하지 않는다.

15번에서 [고정/명시적 우선순위 선택](model-routing.md)을 추가했다.
가격·지연에 따른 선택이나 실패 후 폴백은 아직 없으며 16~17번에서 다룬다.
컨텍스트 크기는 현재 메타데이터이며 입력 토큰 추정·실시간 예산 검사는 아직 하지 않는다.
공급자 실제 지원과 문서/모델 품질은 별도 실연결 평가가 필요하다.

설정·카탈로그 변경 뒤 서비스는 재시작한다. 분석 서비스 인스턴스는 캐시하므로
파일의 enabled만 바꿔 실행 중 인스턴스를 즉시 폐기하는 hot reload는 제공하지 않는다.
readiness는 파일을 재검사해 먼저 미준비가 될 수 있지만 기존 분석 인스턴스는 재시작 전까지 남을 수 있다.
공개 카탈로그 API·관리 화면·프론트엔드 변경은 추가하지 않았다.

## 검증

기본 스텁, 정확한 공급자/모델 일치, 미등록·비활성·요구 기능/한도 부족,
중복·잘못된 JSON/버전/타입/파일 크기, 불변성, 운영 정책 유지,
SDK 생성 전 차단을 검증한다. HTTP 테스트에서도 모델 카탈로그 장애 시
ready 및 분석 503·live 200과 SDK 미호출을 확인한다.
키 입력·실제 모델 호출·규정 입력은 계속 보류한다.

2026-10-20: Python 3.11·3.14 각각 201개 테스트, Ruff·mypy(23개 소스)·포맷 검사 통과.
임시 로컬 포트에서 카탈로그 검사 포함 readiness와 인증 HTTP 스모크 테스트 통과.
Spring과 프론트엔드는 변경하지 않았다.
