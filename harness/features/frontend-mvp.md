# 기능: 프론트엔드 MVP

## 관통 모듈

`front/insai` 전체. 백엔드 REST 계약은 [modules/front-insai.md](../modules/front-insai.md) 표 참조.

## 화면 흐름

```
/login ─────┐
/signup ────┤→ (인증) → /cases
            │
/cases ─────┤  청구인: 내 케이스 목록 + [케이스 개설]
            │  사정사: 배정된 케이스 + (미배정 풀에서 배정받기)
            │
/cases/:id ─┤  탭: 서류요청 | 메시지 | 타임라인 | 결과
            │   - 청구인: 요청 목록 → [작성] → /cases/:id/submit/:docReqId
            │   - 사정사: [서류 요청 추가](청구유형→카탈로그) · 제출물 검토(요약·누락) · 결정 등록
            │
/cases/:id/submit/:docReqId ─ 템플릿 스키마 렌더 → 칸 채워 제출 (임시저장 가능)
```

## 상태·인증 처리

- `api/session.js` — localStorage(`insai.access`, `insai.refresh`) + JWT payload 디코드. **client.js·store 가 공유** (순환 방지: session 은 아무것도 import 안 함, jwt 만).
- `api/client.js` — axios. 요청: `Authorization: Bearer` + `X-User-Id`(=jwt.sub). 응답: 봉투 unwrap → `data`. 401: `/api/auth/refresh` 1회 → 실패 시 `session.clear()` + `location=/login`.
- `stores/auth.js` — `user`(computed), `isClaimant`/`isAdjuster`, `login/signup/logout`. 라우터 가드가 `isAuthenticated` 확인.

## 컴포넌트

- `FormFieldInput.vue` — `FormField` 1개를 타입별 입력으로 (TEXT→input, NUMBER→number input, DATE→date input, SELECT→select, FILE→file, 추후). `required`·`maxLength`·`min`·`max` 반영. v-model.
- `CaseTimeline.vue` — `GET /cases/:id/timeline` 를 시간순 리스트로.
- `MessageThread.vue` — 작성(POST). 조회 API 없어 당분간 타임라인 MESSAGE_POSTED 로 근사 or 백엔드 추가 대기.
- `DocumentRequestList.vue` — 상태 뱃지(PENDING/SUBMITTED/ACCEPTED/REJECTED), 청구인은 [작성], 사정사는 [검토]/[반려].

## 스타일

UI 라이브러리 없이 경량 자체 CSS (의존성·버전 리스크 최소화). 중립 팔레트, 카드/버튼/폼 기본 컴포넌트.

## 백엔드 블로커 (프론트 진행 전/중 필요)

| 필요 | 상태 |
|---|---|
| `GET /api/cases` (내 케이스), `?unassigned=true` | ❌ (`CaseView` record 만 있음) |
| `GET /api/cases/{id}` (상세) | ❌ |
| `GET /api/templates?code=life.*` (code→id) | ❌ |
| `GET /api/cases/{id}/messages` (스레드) | ❌ |
| `POST /api/cases/{id}/review` (→ UNDER_REVIEW) | ❌ (결정 등록 전제) |
| ADJUSTER 활성화 (데모) | ❌ (psql 또는 dev 엔드포인트) |

→ 프론트 착수 시 이들 중 최소 `GET /cases`·`GET /cases/{id}` 부터 백엔드에 추가.

## 상태·TODO

- [x] 스캐폴드 정리, 안정 버전, Vite 프록시, `npm install`
- [ ] session/client/store + Login/Signup
- [ ] Cases 목록 + 개설
- [ ] Case 상세 + 서류요청/제출/타임라인
- [ ] 프론트 유닛 테스트 (Vitest)
