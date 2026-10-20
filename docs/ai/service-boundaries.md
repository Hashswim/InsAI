# 작업 03 — Spring Boot와 FastAPI 책임 경계

상태: 아키텍처 결정 완료. 대상은 의료 청구 JEV 경로다.
기존 업무 백엔드는 유지하고 AI 실행을 별도 Python 서비스에 맡긴다.
[JEV 명세](jev-model-spec.md), [권한 정책](ai-authority-policy.md), [API 계약](jev-contract.md)을 따른다.
이 문서는 현재 구현과 후속 목표를 구분한다.

## 1. 서비스 구조

```text
사용자 앱
   │ 청구·파일 등록 / AI 분석 요청·조회
   ▼
Spring Boot :8080 ─────────────── PostgreSQL / 업무 파일 스토리지
   │                           계정·청구·문서·AI 결과의 원본 저장
   │ POST /internal/jev
   │ Bearer 서비스 토큰 + 문서 ID·종류·PDF
   ▼
FastAPI :8085
   ├─ JEV / LangChain / 출력 검증 / 모델 Router ── Gemini
   └─ 원문 없는 운영 정보 ────────────────────── LangSmith
   │
   └─ 결과·근거·신뢰도·모델·trace ID를 Spring에 반환
```

사용자 앱은 FastAPI를 직접 호출하지 않는다. 사용자에게 공개하는 계약은 Spring이 소유한다.
FastAPI는 내부 인증이 된 Spring의 요청을 받아 분석한다.
네트워크 격리와 검증된 사용자 인증은 09번 작업에서 확인·구현한다.

## 2. 책임과 데이터 소유권

| 대상·작업 | 소유 주체 | 경계 규칙 |
|---|---|---|
| 계정·사용자 인증·역할 | Spring auth | FastAPI가 사용자 계정이나 사용자 토큰을 저장·발급하지 않음 |
| 청구 접근 권한·참여자 | Spring collab | 모델 호출·결과 조회 전에 Spring이 검사 |
| 청구 상태·사정사의 최종 Decision | Spring collab | FastAPI와 JEV 추천이 직접 변경하지 않음 |
| 파일 등록·메타데이터·원본 PDF | Spring document | 저장된 파일 ID의 케이스 소속을 확인한 후 바이트 제공 |
| JEV 실행 요청 조립 | Spring ai | 파일 ID·종류를 검증하고 내부 요청 생성 |
| HTTP·공급자 호출 오류 변환 | 각 서비스의 어댑터/API | 상대 서비스의 내부 예외나 원문을 사용자에게 전달하지 않음 |
| JEV 단계·프롬프트·출력 스키마 | FastAPI | 업무 DB 엔티티를 사용하지 않고 전송 DTO만 사용 |
| 모델 선택·공급자 SDK·토큰 집계 | FastAPI | Gemini 키와 SDK는 JEV Spring 코드에 두지 않음 |
| 규정 데이터 | 향후 Spring 측 업무 관리 영역 | 현재 미등록. Python에 임의 규정을 하드코딩하지 않음. 전달 방식은 규정 제공 후 확정 |
| AI 분석 결과의 영구 기록 | Spring ai의 jev_analyses | 권한을 가진 사용자가 Spring에서 조회. AI 결과는 최종 Decision과 별개 |
| 원문 없는 실행 추적 | FastAPI → LangSmith | 허용된 운영 정보만 전송. 업무 결과의 원본 저장소로 사용하지 않음 |
| 향후 큐·작업 상태·중복 실행 키 | Spring 측 작업 관리 영역 | 26번에서 구현. FastAPI는 실행 결과를 반환하는 분석 서비스 유지 |

FastAPI에는 업무 DB 접속 자격증명이나 업무 스토리지의 쓰기 권한을 제공하지 않는다.
현재 Python 서비스는 DB·작업 큐·영구 파일 복사 없이 요청 단위로 처리한다.
메모리상의 PDF와 모델 응답을 LangSmith나 디버그 로그에 기록하지 않는다.
Gemini는 분석을 위한 외부 전송 대상이고, LangSmith는 원문을 보내지 않는 관측 대상이다.

## 3. 현재 동기 호출 계약과 식별자

1. 사용자가 기존 파일 업로드 절차로 세 문서를 등록한다.
2. Spring POST /ai/jev가 사용자 식별자와 caseId, documents[{fileId, kind}]를 받는다.
3. Spring이 케이스 접근, 파일 소속, 종류·중복·PDF 형식·크기를 검사한다.
4. Spring이 request_id를 생성하고 문서 ID·종류·base64 PDF를 FastAPI에 전송한다.
5. FastAPI가 서비스 토큰·PDF 구조·페이지 제한을 검증한 후 Gemini를 호출한다.
6. FastAPI가 출력 스키마·근거 문서/페이지를 검사하고 원문 없는 trace를 기록한다.
7. Spring이 request_id 및 응답 상태 등을 확인하고 결과 JSON을 jev_analyses에 저장한다.
8. DB 저장이 성공한 후 analysisId와 result를 사용자에게 반환한다.
9. GET /ai/jev/{analysisId}는 Spring이 케이스 접근 권한을 다시 검사한 뒤 결과를 반환한다.

| 식별자 | 생성·소유 주체 | 용도 |
|---|---|---|
| caseId | Spring collab | 업무 접근 권한과 분석 결과 연결. 현재 FastAPI 요청에는 포함하지 않음 |
| fileId / document_id | Spring document | 입력 파일과 결과의 근거 연결 |
| request_id | Spring ai | 내부 요청과 응답의 상관관계 확인. 현재 중복 실행 방지 키가 아님 |
| analysisId | Spring ai | 저장 성공한 분석의 영구 조회 키 |
| trace_id | FastAPI 추적 계층 | LangSmith 실행 참조. 비활성·기록 실패 시 null |

내부 요청은 snake_case, 외부 Spring 요청의 업무 필드는 camelCase다.
전체 DTO는 API 계약과 JevGateway/JevResult, Python schemas/jev.py에서 관리한다.
공개 API의 인증 주체와 내부 서비스 인증 주체는 다르다.
Bearer 서비스 토큰이 있다고 특정 사용자의 청구 권한까지 검증된 것은 아니다.

## 4. 실패·저장·재시도의 책임

| 상황 | FastAPI 책임 | Spring 책임 |
|---|---|---|
| 파일 미등록·타 케이스 파일·업무 권한 없음 | 요청이 도달하지 않아야 함 | 모델 호출 전 거부 |
| 내부 인증 없음·미설정 | 현재 401 또는 503 반환 | 외부에 토큰을 노출하지 않고 실패 처리 |
| 잘못된 PDF·입력 | 현재 422, 과대 요청은 413 반환 | 분석 결과를 성공으로 저장하지 않음 |
| 모델 설정 없음·출력 오류·모델 장애 | 현재 설정 미비 503, 분석 실패 502의 일반 오류 반환 | 현재 일반 업무 오류로 변환. 세부 4xx/5xx 매핑은 08·25번에서 개선 |
| 규정 미등록 | ON_HOLD / NOT_REGISTERED인 정상 결과 반환 | 정상 추천 결과로 저장; 청구 상태를 바꾸지 않음 |
| LangSmith 기록 실패 | trace_status=FAILED, trace_id=null로 결과 반환 | 추천 결과 저장 가능 |
| FastAPI 정상 응답 후 DB 저장 실패 | Spring의 저장 성공을 추정하지 않음 | 성공 응답을 반환하지 않음; 현재 분석 결과는 재조회 불가 |
| Spring 읽기 타임아웃·사용자 연결 종료 | 모델 작업이 계속될 수 있음 | 분석 실패/상태 미확정으로 취급. 중복 실행 가능성을 고려 |

현재 Spring HTTP 연결 타임아웃은 5초, 읽기 타임아웃 기본값은 120초,
Gemini 어댑터의 timeout 설정은 90초이며 자동 모델 재시도는 0이다.
이는 전체 요청의 엄격한 120초 완료나 공급자 작업 취소를 보장하지 않는다.
LangSmith 전송 시간·서버 대기·네트워크·DB 저장 시간도 발생한다.

현재 내부 요청을 자동 재전송하지 않는다. 재요청은 새로운 모델 실행과 비용을 발생시킬 수 있다.
분산 트랜잭션·정확히 한 번 실행·실패 결과 영구 저장은 현재 제공하지 않는다.
모델 호출 동안 업무 DB 트랜잭션을 길게 유지하지 않는다. 결과 저장은 Spring의 로컬 DB 트랜잭션으로 처리한다.
사용자에게 성공을 알리는 기준은 Gemini 완료나 trace 생성이 아니라 Spring 결과 저장 성공이다.

## 5. 동기·비동기 처리 기준

현재 MVP는 제한된 PDF 세 개를 한 요청으로 분석하는 동기 API를 유지한다.
자동화된 장시간 대기·작업 재개·백그라운드 처리·작업 ID 조회를 제공한다고 표현하지 않는다.

다음 요구가 생기면 26번 비동기 작업 관리 범위로 전환한다.

- 앱 연결이 종료되어도 실행을 유지하고 결과를 나중에 조회해야 할 때.
- 실측 지연이 앱·프록시의 요청 대기 한도를 자주 초과할 때.
- 단계별 재개, 대량 청구, 동시성 제한, 재시도·취소·중복 실행 방지가 필요할 때.

목표 비동기 흐름은 Spring의 작업 생성·권한 확인 → 내구성 있는 큐 → 작업자에서
동일 FastAPI 내부 분석 계약 호출 → Spring 결과·작업 상태 저장 → 사용자 상태 조회다.
작업자는 큐를 사용하더라도 FastAPI에 업무 DB 쓰기 권한을 넘기지 않는다.
Spring이 작업 ID·상태·idempotency key의 원본을 관리한다.
상태 전이·재시도 예산·큐 제품·실행 중 취소 보장은 26번에서 확정한다.
선택적 LangSmith 장애를 작업 전체의 재실행 사유로 삼지 않는다.

## 6. 기존 구현과의 정합성

| 점검 지점 | 현재 상태 | 후속 조치 |
|---|---|---|
| RunJevService → JevGateway → FastApiJevClient | JEV 내부 HTTP 경계를 사용 | 25번에서 계약·오류·타임아웃 검증 강화 |
| ReadClaimPdfService | document 모듈 내부에서 저장소 접근. 현재 LocalDiskStorage 구체 구현에 결합 | 25번에서 읽기용 스토리지 포트 경계 정리; ai 모듈은 파일 엔티티를 직접 참조하지 않음 |
| JevAnalysis + V6 마이그레이션 | Spring이 caseId·requestedBy·전체 결과 JSON을 소유 | 27번에서 버전·승인·수정·거절 감사 이력 검토 |
| FastAPI provider/router | 현재 Gemini 하나만 지원 | 10~16번에서 공급자 추상화·카탈로그·라우팅 확장 |
| 기존 Java LlmClient와 Anthropic 구현 | 구조화·결정문 평문화의 기존 직접 호출 경로가 남아 있음 | JEV와 구분. 04번에서 유스케이스 우선순위를 정하고 25번에서 전환 여부·계약 처리 |
| harness 기존 아키텍처 | AI 전체가 Java 안에 있는 예정 구조 설명이 남아 있었음 | 이 결정으로 현재 JEV 분리 구조를 반영 |

이 단위에서는 서비스 추가 분리, 기존 AI 기능 제거, 프론트 변경, 규정 추가를 수행하지 않는다.

## 완료 기준

- 각 데이터와 기능의 소유 주체가 하나로 정해졌다.
- 사용자 앱→Spring→FastAPI→Gemini 및 결과 저장 흐름을 정의했다.
- FastAPI의 업무 DB 직접 변경 금지와 최종 판단 경계를 명시했다.
- 요청·응답·오류·식별자·저장 성공 기준을 구분했다.
- 현재 동기 처리의 한계와 비동기 전환 조건·소유권을 정했다.
- 코드에서 확인한 현재 상태와 후속 구현 항목을 분리해 기록했다.

현재 파일·호출 경로를 근거로 한 설계 결정이다. 실모델 지연이나 장애 복구의 운영 검증 결과는 아니다.
