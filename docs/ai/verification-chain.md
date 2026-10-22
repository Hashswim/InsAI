# 작업 22 — V 단계: 최종 구조적 검증 체인

## 구현 범위

`app/jev/verification.py`의 `VerificationChain`을 J→E 다음에 연결했다.
J의 모델 초안과 E의 참조 검사 결과를 대상으로 최종 구조 검사를 수행한다.
독립 모델 재판정, OCR/문장 대조, 규정 적용이나 신뢰도 보정 모델은 구현하지 않는다.
모델 호출은 기존처럼 한 번이며 E/V 때문에 모델 호출·비용을 추가하지 않는다.

V는 기존 공통 엄격 검증기로 결과를 다시 읽고 복사한다. 따라서 수정·직접 구성된 모델도
그대로 통과시키지 않는다. 검사 항목은 다음과 같다.

- 유효한 항목 판정, 근거 존재, 근거·항목 형식.
- confidence는 유한한 JSON 숫자 0–1이며 bool·숫자 문자열은 불가.
- verification_reason은 공백뿐일 수 없음.
- NEEDS_SUPPLEMENT에는 공백 아닌 supplement와 근거가 필요함.
- UNVERIFIABLE의 유효한 수치 신뢰도는 0으로 정규화함.
- 오프라인 stub은 근거 없는 UNVERIFIABLE만 허용하며 모델 자기평가라고 표시하지 않음.

임의의 신뢰도 임계값은 추가하지 않았다. SUITABLE의 신뢰도가 낮다는 이유만으로 자동 보완 판정하지
않고, 높은 신뢰도로 제출·지급 승인하지도 않는다. 보완 안내가 실제로 적절하고 구체적인지는
프롬프트·사람의 검토·후속 평가 대상이며, 비어 있지 않다는 검사만으로 보증하지 않는다.
판정과 근거 문장의 의미적 일치, 사실성, 서로 다른 항목 간 의미적 모순도 자동 검증하지 않는다.

## 서버가 제공하는 verification 필드

모델의 Findings에는 이 필드를 추가하지 않는다. V를 통과한 뒤 서버가 `VerifiedResult`를 만들고,
응답·Spring 저장 결과에 다음 필수 객체를 포함한다.

```json
{
  "verification": {
    "method": "STRUCTURAL",
    "factual_consistency": "NOT_VERIFIED",
    "confidence_basis": "MODEL_SELF_ASSESSMENT",
    "human_review": "REQUIRED"
  }
}
```

stub의 confidence_basis만 OFFLINE_STUB이다. 모델이 이 필드를 출력하면 Findings의 알 수 없는
필드로 거절한다. 이전 내부 결과에 검증 표시가 들어 있어도 J/E가 재구성하고 V가 서버 상수를 다시 만든다.
이 표시는 검증 범위를 알리기 위한 것이며 통계적 정확도나 제출 가능 표시가 아니다.

제공자 포트 ProviderResult는 변경하지 않았다. 서버의 VerifiedResult는 이를 확장한 별도 내부 타입이며,
최종 서비스·재시도 실행 결과는 이 타입을 사용한다. V 자체를 직접 호출할 경우 PDF 참조 검사는 하지 않으며
실제 운영 경로 `run_workflow`가 J→E→V 순서를 보장한다.

## 저장·추적·호환성

- 성공 trace에만 verification_method/factual_consistency/confidence_basis/human_review 상수를 추가한다.
  해당 값도 스키마를 검증하고, 입력·출력·판정·근거·사유 원문은 전송하지 않는다.
  실패 trace에 V 검사 통과 표시를 붙이지 않는다.
- Spring은 verification 객체와 네 필드, 공급자와 신뢰도 출처의 일관성을 검사한다.
  누락·null·사실 검증 완료/보정된 신뢰도/사람 검토 불필요를 주장하는 값은 AI_INVALID_RESPONSE다.
- 업무 결과 JSON에 그대로 저장·재조회하므로 DB 컬럼이나 마이그레이션은 필요하지 않다.
  과거 저장 JSON의 없는 verification은 null로 읽으며 현재 검사 결과를 추정해서 채우지 않는다.
- 새 HTTP 성공 응답에는 verification이 필수이므로 Spring과 FastAPI를 함께 배포한다.
  프론트엔드 코드는 변경하지 않았다. 과거 저장 결과는 최신 검증 정책 통과 결과가 아니다.

작업 22 당시 체인은 jev-chain-v5다. 현재 작업 23의 [오케스트레이터](jev-orchestrator.md)는
jev-chain-v6으로 순서·실패 중단·시도별 상태를 조정한다. 프롬프트는 변경 없는 jev-v2다.
실제 환경변수·키 설정은 건드리지 않았으며
규정 파일은 비어 있다. submission_status=ON_HOLD / regulations_status=NOT_REGISTERED를 유지한다.

## 실패와 검증 근거

잘못된 초안은 기존 고정 오류를 통해 HTTP 502 / INVALID_AI_RESPONSE로 종료한다.
부분 결과 저장, 자동 재호출·fallback, 모델 사유의 자동 생성·수정은 하지 않는다.
구조 검사는 원문 참조·중복 검사의 J/E 책임을 대체하지 않는다.

합성 초안으로 세 판정과 0/0.01/0.99/1의 신뢰도를 검사하고, 확인 불가 정규화,
빈 근거·사유·보완사항, 비정상 수치, stub 승격 차단, 결과 복사·불변 메타데이터,
모델의 검증 범위 조작 차단과 trace 원문 차단을 검사한다.
공유 계약·실제 HTTP 수신·DB 저장/재조회·과거 JSON 읽기도 검증한다.

실제 의료 문서·실제 모델 호출을 이용한 의미적 정확도 평가를 수행한 것은 아니다.
현재 V는 **구조적 검증**이며, 명세의 의미적 판정-근거 일치 목표를 모두 달성했다는 뜻이 아니다.
