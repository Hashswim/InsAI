# 로컬 OpenAI 모델 선택 — 2026-10-21

사용자 요청에 따라 최초 로컬 연결 모델을 Gemini에서 OpenAI로 변경한다.
공식 문서의 최신 계열 중 비용 민감/대량 처리용인 `gpt-6-luna`를 선택했다.
최상위/가장 비싼 모델을 최신이라는 이유만으로 선택하지 않았다.

공식 사양: [Luna](https://developers.openai.com/api/docs/models/gpt-6-luna),
[현재 모델](https://developers.openai.com/api/docs/models), [요금](https://developers.openai.com/api/docs/pricing),
[PDF 입력](https://developers.openai.com/api/docs/guides/file-inputs).
표준 텍스트 토큰 단가는 100만 토큰당 입력 $0.10/출력 $0.50이다.
장문 tier·cache write·지역/처리 모드 등은 추가 요금 조건이 있으므로 한 청구의 고정 가격이 아니다.
계정별 모델 사용 권한/결제와 의료 판독 성능은 실제로 검증하지 않았다.

선택한 로컬 설정:

```dotenv
JEV_PROVIDER=openai
OPENAI_MODEL=gpt-6-luna
OPENAI_REASONING_EFFORT=none
OPENAI_VERBOSITY=low
JEV_MODEL_CATALOG_PATH=config/model-catalog.openai.json
```

`back/ai-service`를 실행 디렉터리로 사용한다. 키/서비스 토큰은 별도로 로컬 `.env`에만 보관한다.
`.env.example`은 실행 시 자동으로 읽지 않는다. 실제 `.env`가 없으면 이 선택 설정도 로드되지 않는다.
예제 파일에 실제 키를 넣지 말고, 이미 넣은 키는 제공자에서 교체한 뒤 Git 관리 대상에서 제거해야 한다.
이번 변경에서는 사용자가 넣은 키를 임의 이동/삭제하거나 외부 API에 사용하지 않았다.

none은 지원되는 추론 단계를 끄고 low verbosity는 불필요한 출력 분량을 줄이는 설정이다.
이를 검증된 의료 추천 품질이나 일정한 토큰 절감률로 해석하지 않는다.
이미지 PDF의 작은 글자를 잃지 않도록 PDF detail을 low로 강제하지 않는다.
필수 항목·근거 JSON이 잘리지 않도록 기존 출력 상한 16000도 유지한다.
이는 항상 16000 토큰을 소비한다는 뜻이 아니다. 실제 사용량은 입력 PDF와 결과에 따라 달라진다.
성능이 부족하면 대표 합성/비식별 검토 자료로 reasoning low와 비교한 뒤 변경한다.

일반 모델에는 새 옵션을 기본 전송하지 않는다. 설정한 경우에만 Responses 요청에 포함한다.
store=false, 자동 재시도 없음, strict JSON Schema, 원문 없는 LangSmith 정책은 유지한다.
새 카탈로그에는 복잡한 tier/cache 비용을 불완전하게 추정하지 않도록 pricing을 넣지 않았다.
비용 guard를 켜려면 기존 정책에 따라 보수적 단가와 예산을 별도 검토해야 한다.
staging/production의 기존 Gemini-only 가드는 이번 로컬 모델 선택만으로 해제하지 않았다.
운영 OpenAI 전환은 개인정보 처리·사용 권한·실연결 검증과 운영 가드 변경을 별도로 진행한다.
