# 작업 28 — 원문 없는 추적·로컬 평가·대시보드 백엔드

상태: 백엔드 구현·합성 데이터 검증 완료. 프론트는 변경하지 않았다.
LangSmith 실제 계정 접속·차트 생성·보존 설정과 실제 Gemini 실행은 하지 않았다.
환경변수 보류, 작업자 기본 비활성, 규정 미등록/ON_HOLD를 유지한다.

## 1. LangSmith 추적 경계

기존처럼 요청당 하나의 수동 `jev-analysis` chain run을 기록하고 inputs/outputs는 빈 객체다.
자동 LangChain 추적은 계속 비활성이다. 새 자식 LLM trace를 만들거나 프롬프트/의료 결과를 업로드하지 않는다.

전송 직전 `checked_metadata`가 허용 키와 값의 타입/범위를 검사한다.
공급자·라우팅·워크플로·검증 한계는 고정 상수, 토큰/시도 수는 비음수 정수,
비용 추정은 유한한 비음수 decimal, 모델은 설정된 식별자 형태만 허용한다.
라우팅/실행/워크플로의 확장은 자신의 허용 키만 사용할 수 있고 모델/공급자 필드를 덮어쓰지 못한다.
알 수 없는 메타데이터는 전송하지 않고 trace_status=FAILED로 처리한다.
이 형태 검사는 모델 이름에 포함된 모든 개인정보를 의미적으로 탐지하는 기능이 아니다.
운영 모델 카탈로그·프로젝트 설정에 환자 정보나 비밀값을 넣지 않아야 한다.

SDK 설정도 hide_inputs/outputs와 metadata 검사 함수를 사용하고,
`omit_traced_runtime_info=True`로 SDK의 런타임/환경 메타데이터 자동 추가를 막는다.
sampling은 1로 명시하여 환경의 샘플링 값에 의해 조용히 누락되는 경우를 줄인다.
자동 배치는 사용하지 않는다. 연결/읽기 timeout은 각각 2초/3초다.
SDK 내부 재시도·DNS 등을 포함하는 전체 추적 deadline이라고 보장하지 않는다.
SDK 기능이 지원되지 않거나 전송/검사가 실패하면 원문 예외를 노출하지 않고 FAILED가 된다.
SDK 버전 변경 시 실제 SDK 직렬화 경계 테스트를 다시 통과해야 한다.

RECORDED는 create_run 호출이 예외 없이 완료됐음을 나타내며 UI 반영/보존/다운스트림 영구 저장까지 확인한 것은 아니다.
DISABLED는 미설정/비활성이고 FAILED와 구분한다. 추적 장애만으로 모델을 재실행하지 않는다.
LangSmith로 사용자·케이스·작업·문서 ID, 원문/근거/판정, 파일명, 예외 원문을 새로 보내지 않는다.

공식 참고: [입출력과 메타데이터 보호](https://docs.langchain.com/langsmith/mask-inputs-outputs),
[Client 설정](https://reference.langchain.com/python/langsmith/client/Client).

## 2. 대시보드용 케이스 통계 API

GET `/ai/jev/cases/{caseId}/metrics?days=7`

기존 사용자 Bearer JWT와 케이스 참여 권한을 검사하고 조회 허용/거절을 감사에 기록한다.
days는 1~31, 기본 7이다. 전사 통계/관리자 권한을 새로 개방하지 않는다.
프론트는 향후 이 API를 호출할 수 있다. 현재 화면 자체를 구현한 것은 아니다.

응답 ApiResponse.data:

| 필드 | 의미 |
|---|---|
| since, until | UTC Instant 기준 반개구간 [since,until) |
| storedAnalyses | 기간 내 생성된 해당 케이스의 저장 결과 수 |
| inputTokens, outputTokens | 그 저장 결과에 기록된 토큰 필드의 합 |
| tokenMetadataRows | 토큰 컬럼을 기록한 결과 수; 실제 공급자 사용량의 완전성을 뜻하지 않음 |
| traceStatuses | RECORDED / DISABLED / FAILED / UNKNOWN별 저장 결과 수 |
| jobStatuses | 기간 내 접수된 작업의 현재 QUEUED / RUNNING / SUCCEEDED / FAILED / UNKNOWN별 수 |

DB aggregate projection만 사용하며 결과 JSON/PDF를 읽거나 파싱하지 않는다.
응답에는 케이스·사용자·작업·결과 ID, 판정·근거·모델 원문이 없다. no-store다.
반복 읽기 트랜잭션으로 여러 집계 쿼리의 스냅샷을 맞춘다.
V10은 nullable 토큰 컬럼과 케이스/시각 복합 인덱스를 추가한다.
과거 행의 토큰 수·추적 상태를 소급 생성하지 않는다. 미기록 trace는 UNKNOWN이다.

주의:

- storedAnalyses와 SUCCEEDED job은 겹치는 집합이므로 더해서 총 요청 수로 쓰지 않는다.
- job은 **접수 시각**, 결과는 **결과 생성 시각**으로 기간을 판단한다. 기간 이전 접수된 작업은 job 집계에 빠질 수 있다.
- 동기 실패, 실패한 공급자 시도/재시도, 저장 실패는 저장 결과 토큰 합에 포함되지 않는다.
- 공급자 사용량이 없으면 기존 응답의 0이 저장될 수 있다. 이 합은 청구서·실제 비용·전체 사용량이 아니다.
- trace FAILED 비율은 모델 실패율이 아니다. trace 기록이 된 결과도 ON_HOLD일 수 있다.
- 실시간 push·장기 시계열·p95/비용·전역 대기열/worker 생존 통계는 이번 API에 없다.

## 3. LangSmith에서 확인할 차트와 남은 계정 작업

사용자가 환경변수 준비를 재개한 뒤, 검토한 서버 환경에서 기존 LangSmith 프로젝트를 지정한다.
합성 문서로 단일 사이클을 실행하여 Spring 저장 trace ID와 프로젝트 run을 확인한다.
run inputs/outputs가 비어 있고 첨부·런타임/환경·환자 데이터가 없는지 직접 점검한다.
프로젝트 접근 권한·보존 기간·삭제 정책은 계정 관리자가 별도로 적용해야 한다.
이번 작업에서 계정·프로젝트나 외부 대시보드 설정을 변경하지 않았다.

프로젝트 Dashboard/Monitoring에서 다음을 점검한다.

- `jev-analysis` root chain의 실행 수·지연·오류율.
- provider/model/prompt_version/chain_version 메타데이터별 필터·그룹.
- 실행별 metadata의 시도/재시도/폴백/서킷 skip과 마지막 워크플로 단계.
- 비용 guard가 켜져 있을 때의 cost_estimate_usd 및 cost_accounting(추정/예약 상한 구분).

현재 토큰과 비용은 일반 metadata에 저장하고 LLM child run이나 표준 usage/cost 필드를 만들지 않는다.
따라서 기본 LLM Calls/Cost & Tokens 패널이 자동 채워진다고 주장하지 않는다.
케이스별 저장 토큰 합은 위 Spring API, 개별 실행 토큰/추정 비용은 run metadata를 확인한다.
임의 수치 metadata의 차트 집계 지원은 계정/제품 UI에서 별도로 확인해야 한다.
합성 구조 평가를 임상적 정확도/보정 신뢰도 feedback 점수로 업로드하지 않는다.

메뉴/리전별 차트 차이는 [공식 대시보드 문서](https://docs.langchain.com/langsmith/dashboards)를 따른다.
원문을 보기 위해 자동 추적이나 첨부 업로드를 켜지 않는다.

## 4. 로컬 합성 평가

`back/ai-service`에서 `make evaluate`를 실행한다. 실제 모델/키/문서/원격 dataset이 필요 없다.
내장 코드가 메모리의 빈 1페이지 PDF 세 개와 합성 항목을 만들고 실제 J/E/V 흐름을 실행한다.

사례 5건: SUITABLE 수용, NEEDS_SUPPLEMENT 수용, UNVERIFIABLE 수용,
다른 문서 ID 거절, 실제 페이지 범위를 벗어난 근거 거절.
음성 사례는 E 단계에서 실패해야 통과하며, 무관한 오류를 성공 판정하지 않는다.
출력은 고정 case 이름·통과 여부·건수·활성 릴리스 식별자만 있는 JSON이다.
실패하면 exit 1이며 CI에 evaluate 검사를 추가했다.
임의 의료 파일 입력·원문 출력·LangSmith dataset/experiment 업로드·LLM judge는 제공하지 않는다.

이는 구조 회귀 평가다. PDF 텍스트를 읽어 판단하는 의료 정확도,
누락 항목 탐지 재현율, 근거의 사실성, 규정 준수, 확신도 보정 성능을 검증하지 않는다.
규정/라벨링한 검토 데이터와 승인된 평가 계획이 생긴 후 별도 검증해야 한다.

## 5. 검증 범위와 운영 보류

SDK 변환 이후 런타임/환경 비노출, 확장 metadata 차단, 원격 실패 비전파,
합성 평가 무네트워크, 통계 케이스 격리·인가·빈 집계·기간·토큰 합·원문 미조회,
H2의 V10 SQL/과거 null 호환을 검증한다.
실제 Gemini/LangSmith 연결, 운영 PostgreSQL, 대시보드 UI, 부하·관측 알림·개인정보 보존 정책은 미검증이다.
28개 단위의 백엔드 구현이 끝났어도 운영 배포 승인이나 임상·규정 판정 성능 검증 완료를 의미하지 않는다.
