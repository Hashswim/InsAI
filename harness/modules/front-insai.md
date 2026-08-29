# 모듈: front/insai  (Vue 3 SPA, JavaScript)  — 착수 직후

## 목적

청구인/사정사가 쓰는 웹 UI. 백엔드 REST 를 소비. 로그인, 케이스 목록/상세, 서류 요청, 양식 제출, 메시지, 타임라인, 결정.

## 경로

- `front/insai/`
- 스택: Vue 3.5, Vue Router 4, Pinia 2, axios, Vite 6, Vitest (모두 안정 버전으로 고정 — 스캐폴드의 RC 채널·oxlint 제거)

## 구조 (계획)

```
src/
├── main.js                 앱 초기화, 세션 복원
├── App.vue                 레이아웃 + <RouterView>
├── router/index.js         라우트 + 인증 가드
├── lib/jwt.js              JWT payload 디코드 (검증 X, sub/roles 추출)
├── api/
│   ├── session.js          토큰 원천 (localStorage + jwt 디코드). client·store 공유, 순환 방지
│   ├── client.js           axios 인스턴스: baseURL '/api', Bearer+X-User-Id 주입, 봉투 unwrap, 401→refresh 1회
│   ├── auth.js  cases.js  forms.js  catalog.js
├── stores/auth.js          Pinia: user(computed from session), isAdjuster/isClaimant, login/signup/logout
├── views/                  LoginView, SignupView, CasesView, CaseDetailView, NewCaseView, SubmitFormView
└── components/             AppHeader, FormFieldInput(타입별 입력), CaseTimeline, MessageThread, DocumentRequestList
```

## 규약

- **모든 API 는 `/api` 프리픽스.** Vite dev 프록시가 `/api/*` → `http://localhost:8080/*` (rewrite 로 `/api` 제거). 프론트 라우트(`/login`,`/cases`)와 충돌 방지 + CORS 회피.
- axios 응답 인터셉터가 `{success,data,error}` 봉투를 벗겨 `data` 반환, `error` 는 `Error(code,status)` 로 throw.
- 인증: login → `{accessToken, refreshToken}`. accessToken 의 `sub` = 내 userId → `X-User-Id` 헤더로 사용. `roles` = `["ROLE_CLAIMANT"]` → `CLAIMANT` 로 정규화.
- 401 → `session.refreshToken` 으로 `/api/auth/refresh` 1회 재시도 → 실패 시 로그아웃 + `/login`.

## 백엔드 계약 (현재)

| 화면 | 호출 |
|---|---|
| 로그인 | `POST /api/auth/login` |
| 회원가입 | `POST /api/auth/signup` (ADJUSTER 는 PENDING → 로그인 403) |
| 케이스 목록 | ⚠️ `GET /api/cases` **백엔드 미구현** |
| 케이스 상세 | ⚠️ `GET /api/cases/{id}` **백엔드 미구현** |
| 케이스 개설 | `POST /api/cases` |
| 서류 요청 카탈로그 | `GET /api/catalog/life/claim-types`, `.../{type}/required-documents` |
| 서류 요청 생성 | `POST /api/cases/{id}/document-requests` (templateId 필요 → code→id 해석 API 도 없음) |
| 서류 요청 목록 | `GET /api/cases/{id}/document-requests` |
| 양식 스키마 | `GET /api/templates/{id}` |
| 양식 제출 | `POST /api/submissions` |
| 제출물 조회 | `GET /api/submissions/{id}` |
| 메시지 | `POST /api/cases/{id}/messages` (스레드 조회 API 없음 — 타임라인으로 대체 or 추가 필요) |
| 타임라인 | `GET /api/cases/{id}/timeline` |
| 결정 등록 | `POST /api/cases/{id}/decisions` |

## 주의 (gotchas)

- Node 26, `npm create vue@latest` 스캐폴드가 `vue:"rc"` + oxlint 피어 충돌 → `package.json` 재작성해 해결. `.oxlintrc.json`/`eslint.config.js`/`counter.js`/`App.spec.js` 삭제.
- 백엔드 `SecurityConfig` 는 CORS 미설정 → 반드시 Vite 프록시 경유 (직접 `:8080` 호출 시 브라우저 차단).
- 프론트 유닛 테스트는 아직 없음 (TDD 강조는 백엔드 한정).

## 상태·TODO

- [x] 스캐폴드 정리 + 안정 버전 고정 + `npm install`
- [x] `vite.config.js` 프록시
- [ ] api/session/client/store 기반
- [ ] Login/Signup → 케이스 목록 → 상세 → 제출 순으로 구현
- [ ] 백엔드 블로커: `GET /cases`, `GET /cases/{id}`, `GET /templates?code=`, 메시지 스레드 조회
