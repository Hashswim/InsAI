# 작업 27 — 결과 저장과 감사 이력 연결

상태: 구현·합성 데이터 테스트. 실제 모델/운영 PostgreSQL·DB 권한 분리·운영 장애 검증은 별도다.
프론트, 빈 규정, 작업자 기본 비활성, 환경변수 보류는 유지한다.

## 결과 원본과 식별자

`jev_analyses.result_json`은 기존 전체 결과 원본을 유지한다.
새 결과에는 request_id, job_id(비동기만), provider, model, prompt_version,
prompt_sha256, chain_version, trace_id, trace_status를 별도 컬럼에도 같은 결과에서 복사한다.
trace_status=FAILED/DISABLED와 null trace ID도 그대로 보존하고 실제 추적 성공을 꾸며내지 않는다.
job_id 유니크 제약으로 한 작업에 결과가 두 개 연결되지 못하게 한다.
request_id는 상관관계 값이지 유니크/인가 키가 아니다.

결과 엔티티는 애플리케이션에서 immutable로 다루고 수정 API를 제공하지 않는다.
새 분석은 새 행이다. 과거 행의 비어 있는 메타데이터를 현재 배포 버전으로 채우지 않는다.
기존 결과 JSON 조회도 이전 파서를 유지한다. 오래된 JSON의 버전 필드와 새 컬럼 null은 모순이 아니라
당시 구조화 컬럼을 기록하지 않았음을 뜻한다. 저장 원본과 모델 추천을 최종 승인/Decision으로 바꾸지 않는다.

## 접근 감사와 실행 감사의 구분

기존 `jev_access_audits`는 ANALYZE/READ 권한의 ALLOWED/DENIED 기록이다.
ALLOWED는 모델 성공이나 결과 저장 성공이 아니다.
새 job_id 컬럼으로 작업 재조회·재접수·결과/이력 조회를 연결한다.
접수의 사전 허용 기록은 별도 트랜잭션으로 남고, 실제 JOB_ENQUEUED 기록은 작업과 함께 커밋한다.

새 `jev_execution_events`는 원문 없는 실행 사건 장부다.
각 행에는 request_id, job_id, analysis_id, case_id, actor_id,
사건 타입, 허용된 오류 코드, 생성 시각만 있다.
모델 판정·근거·신뢰도·PDF·base64·예외 메시지·토큰/비밀값은 넣지 않는다.
오류 코드는 ErrorCode 또는 EXECUTION_UNCERTAIN만 허용한다.

| 경로 | 기록 |
|---|---|
| 동기 저장 성공 | RESULT_STORED + analysis_id |
| 동기 실패 | FAILED + 고정 코드, 결과 ID 없음 |
| 비동기 접수 | ENQUEUED + job_id |
| 실행권 획득 | STARTED + job_id |
| 비동기 저장 성공 | RESULT_STORED / SUCCEEDED + 같은 analysis_id |
| 비동기 실패 | FAILED + 고정 코드 |
| 중단 결과 불명 | UNKNOWN + EXECUTION_UNCERTAIN |

FAILED 사건만으로 모델이 호출됐거나 과금됐다고 단정할 수 없다.
권한/입력/저장 단계 실패도 포함된다. 동기 API에는 별도 STARTED 사건을 만들지 않는다.
LangSmith 장애는 trace_status에 반영하며 정상 추천 결과의 재실행 사유로 삼지 않는다.

## 트랜잭션과 실패 의미

공통 `JevResultStore`가 새 결과와 RESULT_STORED를 같은 짧은 트랜잭션으로 저장한다.
비동기는 여기에 job 성공 상태·SUCCEEDED도 같은 트랜잭션으로 묶는다.
필수 감사 행 저장에 실패하면 결과와 성공 상태도 롤백된다.
접수/STARTED/FAILED/UNKNOWN도 해당 상태 변경과 사건 기록을 한 트랜잭션으로 묶는다.
수신 결과의 request_id가 현재 실행과 다르면 저장 전에 AI_INVALID_RESPONSE로 차단한다.
상세 응답 검증은 기존 내부 클라이언트가 담당한다.

모델 호출 중 DB 트랜잭션을 유지하지 않는다.
동기 실패는 별도 REQUIRES_NEW 트랜잭션으로 코드만 기록하고 원격 예외/cause를 반환하지 않는다.
DB 전체 장애라면 실패 사건조차 기록할 수 없으므로
성공 사건이나 저장 완료를 추정하지 않는다. 외부 원격 실행과 로컬 저장의 정확히 한 번 완료는 보장하지 않는다.
프로세스가 동기 모델 호출 도중 종료되면 실패 사건이 없을 수 있다.
비동기는 다음 작업자 청소에서 UNKNOWN으로 연결한다.

중단 탐지는 한 tick당 최대 100개 후보 ID를 선택하고 각각 잠근 뒤 현재 상태를 다시 검사한다.
잠금 전 조회한 오래된 엔티티를 재사용하지 않아 완료된 상태를 UNKNOWN으로 덮어쓰지 않게 한다.
이미 종료된 작업의 중복 완료/실패/청소는 사건을 추가하지 않는다.

## 이력 조회 API

- GET `/ai/jev/{analysisId}/history`
- GET `/ai/jev/jobs/{jobId}/history`

기존 Bearer JWT와 케이스 참여 권한을 확인하고 조회 허용/거절을 접근 감사에 기록한다.
응답은 기존 ApiResponse 봉투의 data 배열이며 각 항목은 eventId, requestId, jobId,
analysisId, type, errorCode, occurredAt만 포함한다. 원본 결과와 사용자/케이스 ID는 반환하지 않는다.
비동기 결과의 이력도 연결된 job 전체 사건을 반환한다.
request_id로 사건을 합치거나 인가하지 않으므로 같은 request ID를 재사용한 다른 작업과 섞이지 않는다.
과거 결과에 당시 사건 기록이 없으면 빈 배열을 반환한다. 사건을 소급 생성하지 않는다.
최대 100행, 생성 시각과 ID 순서로 정렬한다. 같은 시각의 사건은 ID로 정렬하므로 엄밀한 인과 순서 보장은 아니다.
현재 한 작업의 정상 사건은 최대 4개이며 별도 페이지 토큰은 제공하지 않는다.
응답은 no-store다. 공개 request_id 검색 API나 관리자 전체 감사 다운로드는 제공하지 않는다.

## 마이그레이션·검증·한계

V9는 과거 행과 호환되는 nullable 메타데이터, job 유니크/FK, 접근 감사 job_id,
실행 사건 테이블과 조회 인덱스를 추가한다. 과거 사건/메타데이터를 자동 복원하지 않는다.
실행 감사에는 미확인/삭제된 대상의 실패도 남길 수 있도록 대상 ID의 FK를 강제하지 않는다.
결과와 job의 상호 FK는 실제 결과/작업 삭제 순서 및 보존 정책을 운영에서 고려해야 한다.

검증은 동기·비동기 결과 메타데이터, 성공/실패/UNKNOWN 사건, 감사 실패 롤백,
인가·원문 비노출·request ID 재사용 격리, 과거 결과 호환과 H2의 V8→V9 SQL 적용을 포함한다.
실제 PostgreSQL 마이그레이션/경합·장애 검증은 미실행이다.

엔티티 immutable와 저장 전용 repository는 애플리케이션의 갱신 실수를 줄이는 장치다.
DB 관리자/직접 SQL에 대한 WORM·암호학적 위변조 방지·DB 역할별 삭제 차단을 제공하지 않는다.
감사 DB 접근 제어, 백업·보존·삭제 정책, 개인정보 접근 통제는 운영에서 별도 확정해야 한다.
사람의 승인·수정·거절 워크플로와 승인 서명은 이번 작업 범위가 아니다.
LangSmith에는 이 장부나 사용자/케이스/작업 ID를 새로 전송하지 않는다.
