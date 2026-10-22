# 멀티 에이전트 · 약관 RAG · MCP 서버

서류가 갖춰진 청구 건의 J 단계를 LangGraph 멀티 에이전트로 처리하고, 금융감독원 표준약관과
보험사 2곳의 실손 약관을 RAG로 붙이며, 같은 기능을 MCP 서버로 노출한다. 빠른 검사에서 누락이 보이는 건은
여기까지 오지 않는다([규칙 사전 점검](rule-precheck.md)).

## 1. 멀티 에이전트 (`app/agents/`)

```
START → Supervisor ─┬─ ① 서류 검토 ─┐
                    ├─ ③ 의심 검토 ─┼→ Supervisor ─(MEDIUM·HIGH 신호)→ ② 약관 재검색 → Supervisor → ④ 요약 → END
                    └─ ② 약관 검색 ─┘              └──────────────(신호 없음)─────────────────────→ ④ 요약
```

- **Supervisor**: LangGraph 라우팅 노드. LLM을 호출하지 않는다. 첫 단계에서 ①②③을 병렬로 보내고,
  ③이 MEDIUM 이상 신호를 내면 ②에 "보상하지 않는 사항" 재검색을 한 번 맡긴 뒤 ④로 넘긴다.
- **에이전트**: LangChain `ChatOpenAI.with_structured_output(..., method="json_schema", strict=True,
  include_raw=True)`. 출력은 서버가 모든 검증기를 다시 실행한다. 토큰은 응답의 `usage_metadata`로 집계한다.
- **입력**: ①③은 빠른 검사가 만든 텍스트 폼(가명 처리), ②는 진단·진료 사실과 검색된 조항, ④는 ①②③의 결과만 받는다.
- **조립**: `MultiAgentProvider`가 팀 결과를 `Findings(items, risk_signals, summary)`와 `policy_refs`로 묶어
  기존 `Provider` 포트로 돌려준다. 재시도·회로 차단·비용 상한·E/V 검증은 기존 코드를 그대로 쓴다.
- **설정**: `JEV_MULTI_AGENT=true` (OpenAI 또는 Gemini). 꺼져 있으면 기존 단일 호출 경로를 쓴다.
- **프롬프트**: `jev-v7.txt` 한 파일에 `SINGLE`·`REVIEWER`·`RISK`·`POLICY`·`SUMMARY` 구역을 두고 SHA-256으로 고정한다.

## 2. 약관 RAG (`app/rag/`)

- **원천**: 국가 기관 1곳과 보험사 2곳으로 작게 구성한다. 금융감독원 [별표15] 표준약관(2026-09-11, HWP)의
  `<실손의료보험>`, 삼성화재 다이렉트 실손의료비보험 약관 PDF, 한화생명 e실손의료비보장보험 약관 PDF(2026-01-05).
  본문 페이지만 추출해 상품 경계에 `□` 표시 줄을 넣고(`rag/data/*.txt`), 조(제N조)·붙임 단위로 나눈다.
  1,200자를 넘는 조는 줄 단위로 다시 나눠 모두 416개 조항이다. 임베딩 텍스트 앞에는 `[상품] 제N조(제목)`을 붙인다.
  같은 내용도 보험사마다 조 번호가 다르다(청구 서류: 표준약관 제7조, 삼성화재·한화생명 제9조).
- **저장**: `pgvector/pgvector:pg16` 이미지의 기존 Postgres, 테이블 `rag_policy_chunks`
  (조항 ID·상품·조·제목·본문·`vector(1536)`·원천 SHA-256). `python -m scripts.ingest_terms`가 전체를 교체 적재한다.
- **검색**: `text-embedding-3-small`, 코사인 거리. 약관 에이전트는 세 질의(진료 유형별 보상내용,
  보험금 청구 서류, 이 청구의 진단·진료 사실)의 결과를 합쳐 쓴다. 재계획 시에는 면책 질의를 쓴다.
- **인용 규칙**: 에이전트는 검색된 조항 ID와 이유만 출력한다. 조·제목·발췌는 서버가 약관에서 채운다.
  검색되지 않은 ID를 인용하면 결과 전체를 `InvalidProviderResponse`로 거부한다.
- **개인정보**: 검색어는 진단명·질병코드·주호소·치료 계획·진료과로만 만들고 환자 식별 정보를 넣지 않는다.

## 3. MCP 서버 (`app/mcp_servers/`, MCP Python SDK 2.x `MCPServer`)

| 서버 | Tool | Resource | Prompt |
|---|---|---|---|
| `insai-documents` | `extract_fields`, `check_missing` | `insai://required-fields` | `jev_review` |
| `insai-policy` | `search_clauses` | `insai://terms/{clause_id}` | `policy_review` |

- 로컬 stdio로만 실행한다. `extract_fields`는 PDF를 받지만 가명 처리된 텍스트 폼만 돌려준다.
- 서비스 내부의 분석 흐름은 같은 로직을 직접 호출한다(한 프로세스 안에서 MCP 왕복을 피함).

## 4. 계약 변경

- 응답에 `policy_refs`(조항 ID·상품·조·제목·발췌·이유, 최대 10개)를 추가했다. `rules`·`stub` 응답은 빈 배열이어야 한다.
- 청구인 응답에서는 Spring이 의심사항·요약과 함께 `policy_refs`도 제외한다.
- LangSmith에는 J 아래에 에이전트(`llm`)와 pgvector 검색(`retriever`)을 하위 실행으로 남긴다. 입력은 비우고 출력은 토큰 수만 보낸다.

## 5. 검증과 한계

- 테스트: 가짜 채팅 모델과 메모리 벡터 저장소로 병렬 실행·재계획·미검색 조항 거부·검색어의 식별 정보 배제·
  LangSmith 트리 구조를 검사하고, 실제 stdio MCP 클라이언트로 `insai-documents`를 호출한다.
- 실제 실행(합성 서류, gpt-6-luna): 약 12초, 약 9,800토큰. 의심 신호(MEDIUM)로 재계획이 일어났고 조항 4개를 인용했다.
- 약관은 설명용이다. 청구인의 가입 상품을 특정하지 않는다. 조항은 "확인할 조항"이며 보상 여부 판단이 아니다.
- 멀티 에이전트는 서류 완비 건에서 LLM을 4~5회 호출한다(단일 호출 대비 토큰 약 3배).
