# 작업 26 — 비동기 JEV 작업과 상태 저장

상태: DB 기반 작업 큐·작업자·API·통합 테스트 구현. 실제 모델/운영 PostgreSQL 검증은 미실행.
기존 동기 `POST /ai/jev`와 결과 조회는 유지한다. 프론트와 빈 규정 파일은 변경하지 않는다.

## API

`POST /ai/jev/jobs`는 기존 `RunJevCommand`(caseId와 세 documents)를 받는다.
사용자 Bearer JWT와 UUID 형식 `Idempotency-Key`가 필수다.
X-Request-ID는 기존 생성·검증 규칙을 따르며 내부 분석까지 전달한다.
접수 시 권한·파일 소속·PDF 기본 형식과 크기를 검사한다. 모델을 호출하지 않는다.
DB 커밋 후 HTTP 202, Location: `/ai/jev/jobs/{jobId}`, 기존 ApiResponse 봉투를 반환한다.

```json
{
  "success": true,
  "data": {
    "jobId": "작업 UUID",
    "requestId": "최초 접수 실행 UUID",
    "status": "QUEUED",
    "analysisId": null,
    "errorCode": null,
    "createdAt": "접수 시각",
    "startedAt": null,
    "finishedAt": null
  }
}
```

예시의 UUID·시각은 자리표시자다. 실제 응답은 표준 UUID와 ISO 시각이다.
`GET /ai/jev/jobs/{jobId}`는 케이스 참여자만 같은 상태 정보를 조회한다.
작업 성공 후 `analysisId`로 기존 `GET /ai/jev/{analysisId}`에서 결과를 조회한다.
상태 조회는 PDF·문서 목록·결과 본문을 반환하지 않는다. 응답은 no-store다.

같은 사용자+동일 Idempotency-Key는 동일 caseId·종류별 fileId에 대해 같은 작업을 반환한다.
문서 배열 순서는 비교에서 정규화한다. 새 X-Request-ID로 재접수해도 최초 작업의 requestId를 유지한다.
응답 헤더 X-Request-ID는 현재 HTTP 요청 ID이며 data.requestId와 다를 수 있다.
동일 키로 다른 파일/케이스를 접수하면 409다. DB 유니크 제약과 경합 후 재조회로 동시 접수를 처리한다.
FAILED/UNKNOWN 작업도 동일 키로 재접수하면 재실행하지 않는다.
새 키는 새 분석이며, 같은 파일 내용이나 동기 API까지 중복 제거하지 않는다.
키의 범위는 사용자별이고 현재 보존 기간/자동 삭제는 없다.

## 상태와 트랜잭션

`QUEUED → RUNNING → SUCCEEDED / FAILED`, 중단 결과 불명은 `RUNNING → UNKNOWN`이다.

- QUEUED: 요청 참조를 DB에 저장하고 대기한다. 서버 재시작 후에도 남는다.
- RUNNING: 조건부 UPDATE의 승자 한 작업자만 실행한다. 실제 RPC 중에는 DB 트랜잭션을 유지하지 않는다.
- SUCCEEDED: 결과 JSON 삽입과 analysisId·성공 상태 변경을 한 DB 트랜잭션으로 커밋한다.
- FAILED: 코드만 저장한다. 공급자/IO/예외 메시지와 원문은 저장하지 않는다. 자동 재시도하지 않는다.
- UNKNOWN: 시작 후 15분이 지난 RUNNING을 다음 worker tick에서 전환한다.
  원격 호출의 실행·과금 여부가 불확실하므로 자동 재호출하지 않는다.
  늦게 도착한 성공/실패도 이 상태를 덮어쓰지 못한다.

15분은 현재 내부 클라이언트의 최대 300초보다 긴 고정 중단 탐지 여유다.
heartbeat 기반 lease 갱신이나 실행 강제 종료 기능은 아니다.
정상 실행이 15분을 넘으면 다른 작업자의 청소가 UNKNOWN 처리할 수 있으므로
운영 실행 시간·파일 IO·DB 지연을 별도로 관측해야 한다.
UNKNOWN 작업은 실제 모델이 실행되지 않았다는 의미가 아니다.
운영자가 원격 상황을 확인한 뒤 새 키로 재접수할지 판단한다.
분산 환경의 정확히 한 번 실행·단계 재개·취소·자동 재시도는 보장하지 않는다.

## 작업자와 설정

별도 Redis/RabbitMQ 없이 Spring 업무 DB를 내구성 큐로 사용한다.
`JevJobStore`가 짧은 트랜잭션을, `JevJobWorker`가 DB 밖의 실행을 담당한다.
조건부 claim으로 여러 인스턴스가 같은 작업을 동시에 획득하지 못하게 한다.
처리 순서는 생성 시각 기준 우선 선택이며 동시 작업자 환경의 엄밀한 FIFO 보장은 아니다.
기본 스케줄러는 인스턴스당 한 작업씩 처리하고 완료 후 1초 뒤 다음 tick을 실행한다.

`insai.jev.jobs.worker-enabled` / `JEV_JOB_WORKER_ENABLED`의 기본값은 false다.
환경변수 설정 보류와 기존 서버의 의도치 않은 백그라운드 실행 방지를 위한 opt-in이다.
작업자 역할을 맡는 Spring 인스턴스에서 true로 설정해야 실행·중단 탐지가 동작한다.
false여도 접수된 작업은 QUEUED로 저장되므로 운영에서는 작업자 생존·대기열을 반드시 감시해야 한다.
현재 환경변수나 실제 서비스 토큰·모델 키를 채우거나 작업자를 켜지는 않았다.
운영 PostgreSQL에는 V8 Flyway 마이그레이션으로 테이블·유니크 제약·큐 인덱스를 추가한다.
테스트는 H2 PostgreSQL 모드이며 실제 PostgreSQL 경합·장애 시험은 별도다.

## 권한·데이터·감사

대기열에는 사용자/케이스/요청 ID와 파일 ID·종류 참조만 저장한다. PDF/base64/결과는 넣지 않는다.
실행 시 요청자 계정의 ACTIVE 상태와 허용 역할을 다시 확인한다.
기존 runner가 케이스 참여·파일 권한을 재검사하고 저장소에서 PDF를 다시 읽는다.
접수 후 파일 접근권이 없어지거나 계정이 정지되면 모델 호출 전 실패한다.
이는 파일 내용의 불변 스냅샷/해시 검증은 아니며, 저장 파일이 교체되면 실행 시점 바이트를 읽는다.
접수·재접수·상태 조회의 허용/거절은 기존 원문 없는 접근 감사에 기록한다.
감사 저장 실패 시 접수/조회도 실패한다. 27번 [결과·실행 감사](result-audit.md)에서
job_id 연결과 상태 변경의 원자적 실행 장부 및 이력 조회를 추가했다.
LangSmith는 기존 FastAPI 실행 시에만 원문 없는 추적을 남긴다. Spring 큐가 원문을 추가 전송하지 않는다.

## 검증과 남은 운영 범위

통합 테스트: 202→폴링→성공→기존 결과 조회, 인가·키 형식, 동일 키 정규화/충돌,
동시 접수·동시 claim, 작업자 재생성, 실패 시 미저장·재호출 금지,
파일 재인가·계정 정지, UNKNOWN 전환·늦은 결과 차단, 결과/성공 상태 롤백.
V8 SQL 생성 검사는 H2에서 수행한다. 실제 모델·환자 파일을 사용하지 않는다.

외부 큐 제품, 전역 작업 수/접수 속도 제한, 대기열 보존·삭제, 운영 관리자 재처리 UI,
작업자 heartbeat/알림, 부하·PostgreSQL 장애·다중 프로세스 시험은 후속 운영 과제다.
기존 FastAPI 사용량 제한은 실행 시 적용되며 새 접수 API의 대기열 적체 제한을 대신하지 않는다.
이 단계 완료가 운영 준비 완료를 의미하지 않는다.
