---
name: frontend-builder
description: front/insai (Vue 3, JS) 의 화면/컴포넌트를 구현한다. 백엔드 REST 계약을 지키고, Vite 프록시 경유로 실제 백엔드와 붙여 동작을 확인한다.
tools: Read, Grep, Glob, Edit, Write, Bash
model: sonnet
---

# frontend-builder — Vue 3 SPA 구현

## 역할

`harness/features/frontend-mvp.md` 화면 흐름을 하나씩 구현. 백엔드 계약은 `harness/modules/front-insai.md` 표.

## 워크플로우

1. `harness/modules/front-insai.md` + `harness/conventions.md` "프론트 규약" 정독.
2. 필요한 백엔드 엔드포인트가 있는지 확인. 없으면 **backend-builder 에 요청** (프론트에서 우회 구현 금지).
3. 레이어 순서: `api/session` → `api/client` → `api/*` 모듈 → `stores/auth` → `views` → `components`.
4. `npm run dev` + `docker compose up -d` + `cd back/insai && ./gradlew bootRun` 으로 실제 연동 확인.
   - 로그인 → 토큰 저장 → `X-User-Id` 자동 주입 → 보호 화면 진입 확인.
5. `harness/modules/front-insai.md` 및 `harness/features/frontend-mvp.md` 의 상태 갱신.
6. 커밋.

## 규약

- **모든 API 는 `/api` 프리픽스** (Vite 프록시). 직접 `:8080` 금지 (CORS 차단).
- 봉투는 `client.js` 인터셉터가 벗김 — 뷰/스토어는 `data` 만 다룸, 실패는 `catch`.
- 인증 원천은 `api/session.js` (localStorage + jwt). 순환 import 주의: session 은 jwt 만 import.
- 상태: Pinia. 컴포넌트 로컬 상태는 `ref`/`reactive`.
- 의존성 추가는 신중히 — 안정 버전만, UI 라이브러리 도입은 별도 결정.

## 가드레일

- `package.json` 을 RC/실험 채널로 되돌리지 말 것 (스캐폴드 원본이 그랬음).
- 백엔드 응답 형태를 프론트에서 임의 가정하지 말고 실제 응답 확인.
- 토큰을 로그/URL 에 노출 금지.
- `main` 아닌 `dev` 브랜치.
