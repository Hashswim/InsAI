# JEV v1 계약

판정 의미, 단계별 전이와 가상 청구 예제는 [JEV 모델 명세](jev-model-spec.md),
28개 단위 진행 상태는 [작업 진행표](work-units.md)를 참고한다.
AI의 허용 범위와 개인정보·로그 처리 기준은 [권한 정책](ai-authority-policy.md)을 따른다.
데이터 소유권, 실패 처리 책임과 동기·비동기 기준은 [서비스 책임 경계](service-boundaries.md)를 따른다.
기능 우선순위와 MVP 완료 기준은 [유스케이스 우선순위](use-case-priorities.md)를 따른다.
요청 ID 전달, 공통 오류 봉투, HTTP 상태 매핑과 입력 제한은 [공통 API 계약](api-contract-errors.md)을 따른다.
JEV 사용자 JWT·내부 서비스 토큰·케이스 접근 검증은 [인증·인가 안내](service-auth-and-access.md)를 따른다.

J = Judgment(항목 판정), E = Evidence(문서 근거), V = Verification(검증 및 신뢰도).
세 단계는 현재 하나의 Gemini 구조화 응답에서 수행하며, 서버가 출력 형식과 문서/페이지 참조를 검증한다.
독립적인 두 번째 모델에 의한 사실 검증은 아니다. 신뢰도는 모델 자기평가이며 보정된 확률이 아니다.

## 입력
기존 업로드 API로 같은 청구 건에 PDF 세 개를 등록한다.
DIAGNOSIS(진단서), TREATMENT_CONFIRMATION(진료확인서), ITEMIZED_BILL(병원비 세부내역서) 각 한 개.
파일당 최대 10MiB, 30페이지. 암호화 PDF 금지. 이미지 PDF도 Gemini에 직접 전달한다.
Spring은 저장된 파일 ID와 케이스 소속을 확인하고, URL 대신 PDF 바이트를 내부 API로 전달한다.

POST /ai/jev (Spring, :8080)
Authorization: Bearer 로그인 accessToken
Content-Type: application/json

```json
{
  "caseId": "케이스 UUID",
  "documents": [
    {"fileId": "진단서 파일 UUID", "kind": "DIAGNOSIS"},
    {"fileId": "진료확인서 파일 UUID", "kind": "TREATMENT_CONFIRMATION"},
    {"fileId": "세부내역서 파일 UUID", "kind": "ITEMIZED_BILL"}
  ]
}
```

응답은 기존 success/data/error 봉투 안의 analysisId, result.
GET /ai/jev/{analysisId} + 동일한 사용자 Bearer JWT로 저장 결과를 조회한다.
X-User-Id는 JEV의 사용자 식별에 사용하지 않는다.

FastAPI POST /internal/jev 는 Bearer 서비스 토큰으로 보호한다.
request_id, documents[{document_id,kind,content_base64}] 형태의 snake_case JSON.
Spring과 FastAPI는 동일한 JEV_SERVICE_TOKEN을 사용한다.
개발·테스트용 stub 모드는 외부 모델을 호출하지 않는다. Spring에서 그 결과를 받으려면
JEV_ALLOW_STUB=true가 필요하며 기본값은 false다. 운영 환경에서는 stub을 허용하지 않는다.

## 결과
- submission_status = ON_HOLD, regulations_status = NOT_REGISTERED: 규정은 비워 두며 항상 판정 보류.
- items: 항목명, judgment, evidence[], confidence, verification_reason, supplement.
- judgment: SUITABLE / NEEDS_SUPPLEMENT / UNVERIFIABLE.
- evidence: document_id, page(1부터), description.
- 판정 근거가 없으면 SUITABLE/NEEDS_SUPPLEMENT 불가. UNVERIFIABLE의 신뢰도는 0.
- 존재하지 않는 문서/페이지 참조는 응답 실패 처리. 근거 내용의 사실성까지 서버가 보증하지 않는다.
- [E 체인](evidence-chain.md)은 항목 내 중복 근거도 거절하며, 빈 근거를 자동 생성하지 않는다.
- [구조화 출력 파싱 정책](structured-output.md)에 따라 숫자 문자열·bool 페이지/신뢰도,
  중복 JSON 키·빈 필수 설명·잘린 출력은 거절한다. 자동 수정·재호출은 하지 않는다.
- provider, model, prompt_version, prompt_sha256, chain_version, input_tokens, output_tokens.
  새 응답의 버전 세 필드는 필수이며, SHA-256은 소문자 16진수 64자리다.
  [프롬프트·체인 버전 관리](prompt-chain-versions.md)의 배포·과거 결과 호환 정책을 따른다.
- 개발용 provider=stub/model=stub-v1 결과는 실제 판정이 아니라 모두 확인 불가인 연결 확인용 결과다.
- trace_id: 기록 성공 시 UUID, 미설정 또는 전송 실패 시 null.
- trace_status: RECORDED / DISABLED / FAILED.
- AI 결과는 별도 jev_analyses에 저장하며 기존 청구 상태/최종 결정을 변경하지 않는다.

## 원문 저장 금지의 범위
LangSmith 자동 추적을 비활성화하고, 명시적 허용 목록만 수동 전송한다.
PDF, 추출 텍스트, 프롬프트, 판정 내용, 근거, 파일명, 사용자/케이스 ID, 예외 원문을 전송하지 않는다.
공급자/모델, 프롬프트 버전·해시와 체인 버전, 토큰 수, 실행 시작/종료 시각,
별도 허용된 실행·라우팅·비용 메타데이터와 일반화된 성공/실패만 기록한다.
실제 분석 시 PDF 원문은 선택한 공급자(Gemini 또는 선택적 로컬 OpenAI)로 전송된다.
업무용 원본 파일과 분석 결과는 기존 스토리지/업무 DB에 보관된다.
OpenAI의 저장 방지 옵션과 공급자 측 보존 한계는 [어댑터 안내](openai-adapter.md)를 따른다.
LangSmith 기록 실패는 분석 결과 저장을 막지 않고 FAILED로 노출한다.

## 제한
JEV 경로는 JWT와 현재 계정·케이스 권한을 검증한다. 다른 기존 업무 API의 통합 인증과
운영 영구 키·네트워크 구성은 별도 보강이 필요하다. 이 구현은 내부/로컬 MVP 대상이다.
비동기 큐, 중복 실행 방지, 규정 주입, 공급자 실연결 검증은 후속 범위다.
provider는 gemini/stub/openai를 허용한다. OpenAI 어댑터는 모의 테스트만 검증했고
staging/production은 여전히 gemini만 허용한다.
