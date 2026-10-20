# 작업 15 — 모델 Router 정책

상태: 결정적 서버 측 선택 정책과 안전한 선택 이유 추적 구현.
기본값은 기존 고정 모델 선택이며 실제 키·실모델 등록·규정은 추가하지 않았다.
정책 버전은 jev-router-v1이다.

## 정책

| 정책 | 선택 기준 | 선택 불가 시 |
|---|---|---|
| pinned (기본) | 기존 JEV_PROVIDER와 GEMINI_MODEL/OPENAI_MODEL의 정확한 모델 | 다른 후보를 고르지 않고 503 |
| priority (선택) | 같은 공급자 안에서 JEV_MODEL_PRIORITY의 첫 호환 모델 | 전부 부적합하면 503 |

두 정책 모두 카탈로그의 활성 여부, PDF·구조화 출력 지원과 JEV 한도를 먼저 검사한다.
카탈로그 순서·모델 이름·공급자의 일반적 이미지·추정 가격으로 우선순위를 만들지 않는다.
priority는 누락·비활성·미지원 후보를 호출 전에 건너뛸 수 있지만 목록 밖 모델은 선택하지 않는다.
공급자 전환은 하지 않으므로 의료 PDF가 자동으로 다른 사업자에게 전송되지 않는다.
스텁은 pinned에서 명시적으로만 사용하며 실모델의 대체 후보가 아니다.

SDK는 선택된 모델 하나만 초기화한다. 초기화나 분석이 실패하면 다음 모델을 시도하지 않는다.
이는 15번의 기본 단일 시도 동작이다. 16번에서 복구를 명시적으로 켠 API 실행 경로는
동일 공급자 후보를 추가 호출할 수 있고, 최종 이유 코드 SAME_PROVIDER_FALLBACK과 실행 횟수를 기록한다.
16번의 [선택적 복구 정책](model-resilience.md)을 추가했으며 기본 추가 호출은 여전히 없다.
비용/속도 제한은 [17번 정책](usage-limits.md)을 따른다.
현재 가격·지연·품질 평가 데이터가 없으므로 최저가·최고 품질·최적 모델 자동 선택을 주장하지 않는다.

## 서버 설정

기본 pinned에서는 기존 모델 ID 설정을 그대로 쓴다.
선택적으로 다음과 같이 priority를 설정할 수 있다. 아래 ID는 형식 예시이며 실모델 등록이 아니다.

```dotenv
JEV_PROVIDER=gemini
JEV_ROUTING_POLICY=priority
JEV_MODEL_PRIORITY=["reviewed-model-a","reviewed-model-b"]
```

priority는 1~20개의 중복 없는 정확한 ID가 필요하고 모델명 패턴을 검사한다.
pinned에서 priority 목록을 설정하거나 stub에서 priority를 선택하면 설정 오류다.
priority에서는 GEMINI_MODEL/OPENAI_MODEL이 비어 있어도 되며 값이 있더라도 우선순위에 끼워 넣지 않는다.
키·서비스 토큰과 [카탈로그 등록](model-catalog.md)은 그대로 필요하다.
staging/production은 여전히 Gemini만 허용하며 카탈로그/우선순위로 이 제한을 우회할 수 없다.
실제 .env는 생성·입력하지 않았고 .env.example에 기본값과 주석만 추가했다.

사용자 PDF·프롬프트·요청 본문·X-Model/X-Provider 헤더로 라우팅을 변경하지 않는다.
모델 변경 필드가 본문에 있으면 기존 strict 입력 스키마가 422로 거부한다.
프론트엔드와 Spring 계약은 변경하지 않았다.

## 실행과 추적

`select_route`가 불변 RouteDecision을 만든 뒤 `build_routed_provider`가 해당 모델의 어댑터를 만든다.
선택 모델을 설정 복사본에만 적용하고 원래 설정의 모델·키·환경을 수정하지 않는다.
JevService는 선택 결정과 공급자 인스턴스의 provider/model 일치 여부를 검사한다.
성공 응답의 기존 provider/model에는 실제 선택 모델을 기록하므로 Spring의 기존 결과 저장도 이를 사용한다.

LangSmith 활성 시 기존 허용 메타데이터에 아래 값만 추가한다.

- routing_policy: pinned / priority
- routing_reason: OFFLINE_STUB / PINNED_MODEL / FIRST_COMPATIBLE_PRIORITY
- router_version: jev-router-v1
- routing_priority_index: priority 선택 위치(0부터), 해당 정책일 때만 포함

후보 전체 목록·탈락 모델·카탈로그 경로/검토 근거·의료 원문은 전송하지 않는다.
기존 inputs/outputs 비우기와 자동 추적 차단을 유지한다. 분석 실패에도 선택 메타데이터는 동일하다.
선택 자체가 불가능하거나 SDK 생성 전에 실패하면 분석 trace를 새로 만들지는 않는다.
LangSmith를 끄면 선택 이유의 외부 저장은 없으며 별도 DB 필드를 이번에 추가하지 않았다.

## 준비 상태와 수명

readiness도 SDK 생성 없이 같은 선택 함수를 실행하고 checks.routing=SELECTABLE/UNAVAILABLE을 반환한다.
선택 불가는 503이고 기존 model_catalog 상태도 미준비로 표시한다.
준비 응답에는 모델 ID·우선순위·경로를 공개하지 않고 외부 연결은 계속 NOT_PROBED다.

분석 서비스와 설정은 프로세스에서 캐시한다. 선택은 캐시 생성 시에 결정되며
매 요청 적응형 라우팅이나 자동 hot reload는 아니다.
16번 이후에는 고정된 후보 목록과 프로세스별 서킷 상태로 요청마다 복구 경로를 실행할 수 있지만,
카탈로그·서버 정책의 hot reload는 여전히 제공하지 않는다.
설정·카탈로그 변경 후에는 프로세스를 재시작해야 한다. readiness는 변경된 파일을 먼저 읽을 수 있지만
기존 분석 인스턴스는 재시작 전 선택을 유지할 수 있으므로, enabled 변경만으로 즉시 차단된다고 가정하지 않는다.

## 검증

고정 선택 유지, 명시적 우선순위와 카탈로그 순서의 독립성, 호출 전 부적합 후보 제외,
공급자/목록 밖 선택 금지, 잘못된 정책·환경 설정, 단일 SDK 초기화, 실패 후 미재시도,
선택 결정과 공급자 일치, 안전한 trace 메타데이터를 검사한다.
HTTP 검사로 준비 확인의 SDK 미호출, 실제 선택 모델 응답과 trace 연결,
클라이언트 헤더/본문으로 정책을 변경할 수 없음을 확인한다.

2026-10-20: Python 3.11·3.14 각각 221개 테스트 및 Ruff·mypy(24개 소스)·포맷 검사 통과.
라우팅 준비 상태를 포함한 임시 로컬 HTTP 스모크 테스트 통과.
실제 공급자 요청 없이 가짜 모델·trace 클라이언트로 검증했고 Spring·프론트엔드는 변경하지 않았다.
