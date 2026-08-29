# 모듈: 인프라 서비스 (gateway / discovery / config)  (예정 — 미착수)

## 목적

MSA 분리 시점에 등장하는 도메인 없는 서비스. 현재는 모놀리스라 불필요.

## 계획

| 서비스 | 포트 | 역할 | 도입 시점 |
|---|---|---|---|
| **gateway** (Spring Cloud Gateway) | 8080 | 단일 진입점. JWKS 로 JWT 검증 → `X-User-Id`/`X-User-Roles` 주입. CORS, Rate Limit | 첫 서비스 분리 시. 현재 프론트가 `X-User-Id` 직접 전송하는 부분을 여기로 이관 |
| **discovery** (Eureka) | 8761 | 서비스 등록/탐색 | 서비스 3개 이상. K8s 로 가면 K8s DNS 로 대체 가능 |
| **config** (Spring Cloud Config) | 8888 | 중앙 설정 (Git 백엔드) | 선택. K8s ConfigMap 으로 대체 가능 |

## 게이트웨이 이관 체크리스트

1. `com.insuranceai.common.config.SecurityConfig` (permitAll) → 게이트웨이가 검증, 다운스트림은 헤더 신뢰 유지 or Resource Server 재검증(이중 방어)
2. 프론트 `src/api/client.js` 의 `X-User-Id` 주입 제거 (게이트웨이가 주입)
3. `auth` 의 JWKS 엔드포인트를 게이트웨이가 캐시
4. 라우팅: `/auth/**`→auth, `/cases/**`·`/document-requests/**`→collab, `/templates/**`·`/submissions/**`·`/catalog/**`→form, `/files/**`→document, `/ai/**`→ai

## 상태·TODO

- [ ] 전부 미착수 (모놀리스 유지 중)
- [ ] 분리 결정 시 [02-workflow.md](../02-workflow.md) "MSA 분리" 절 따름
