# 모듈: back/insai · com.insuranceai.auth

## 목적

계정 관리, 로그인, JWT(RS256) 발급·검증, 리프레시 토큰 회전. **책임 밖**: 인가(각 모듈이 X-User-Id 로), 게이트웨이 검증.

## 경로

- `back/insai/src/main/java/com/insuranceai/auth/`
- 테스트: `back/insai/src/test/java/com/insuranceai/auth/`
- 스키마: `V1__init.sql` 의 `accounts`

## 핵심 타입

| 타입 | 역할 |
|---|---|
| `domain.Account` | 애그리게이트. email 정규화, role별 초기상태, 상태 전이 | 
| `domain.Role` | CLAIMANT / ADJUSTER / ADMIN. `authority()` = `ROLE_<name>` |
| `domain.AccountStatus` | ACTIVE / PENDING / SUSPENDED |
| `domain.AccountRepository` | `findByEmail`, `existsByEmail` |
| `application.SignupService` | 중복 이메일 CONFLICT, 비밀번호 해시, 저장 |
| `application.LoginService` | 자격검증 UNAUTHORIZED, 비활성 FORBIDDEN, 토큰쌍 발급 |
| `application.TokenRefreshService` | refresh 회전(기존 폐기 + 신규 발급), 재사용/만료 UNAUTHORIZED |
| `application.LogoutService` | refresh 폐기 (멱등) |
| `application.AccessTokenIssuer` (포트) | `issue(accountId, role)` / `parse(token)` + `InvalidTokenException` |
| `application.PasswordHasher` (포트) | `hash` / `matches` |
| `application.RefreshTokenStore` (포트) | tokenId → accountId, TTL |
| `infrastructure.JwtAccessTokenIssuer` | Nimbus RS256. 클레임 sub/roles/iss/iat/exp, 15분 |
| `infrastructure.JwtKeyConfig` | RSAKey 빈(부팅마다 생성), `AccessTokenIssuer` 빈 |
| `infrastructure.BCryptPasswordHasher` / `InMemoryRefreshTokenStore` | 스텁성 구현 |
| `api.AuthController` | `POST /auth/{signup,login,refresh,logout}` |
| `api.JwksController` | `GET /.well-known/jwks.json` (공개키만) |

## 불변식·규칙

- `Account.create`: email → `trim().toLowerCase()`, 공백이면 INVALID_INPUT. role null → CLAIMANT/ACTIVE. **role=ADJUSTER → PENDING** (관리자 승인 후 `activate()` → ACTIVE).
- 평문 비밀번호 미보관 (passwordHash 만).
- `verifyActive()`: ACTIVE 아니면 FORBIDDEN → LoginService 에서 호출.
- `activate()`: PENDING 아니면 CONFLICT.
- refresh 토큰은 불투명 문자열(32바이트 base64url). 회전 시 기존 토큰 즉시 무효.

## 의존 관계

- 들어옴: `collab`/`form` 이 `Role` enum 재사용.
- 나감: `common`, Nimbus JOSE(`spring-security-oauth2-jose`), Spring Security crypto.

## 테스트

`./gradlew test --tests "com.insuranceai.auth.*"` — 44개.
`AccountTest`(도메인 10), `SignupServiceTest`/`LoginServiceTest`/`TokenRefreshServiceTest`(Mockito),
`JwtAccessTokenIssuerTest`(실 RSA, 만료/변조/타키), `AuthControllerTest`(@WebMvcTest),
`AccountRepositoryJpaTest`, `AuthFlowIntegrationTest`(가입→로그인→갱신→로그아웃→폐기토큰 401).

## 주의 (gotchas)

- `JwtKeyConfig` 가 부팅마다 새 RSA 키 → 재시작 시 기존 토큰 전부 무효. 운영 전 시크릿/키스토어 로드로 교체.
- `signup` 응답에 accountId 없음. 프론트는 login 후 accessToken 의 `sub` 클레임을 디코드해 X-User-Id 로 쓴다.
- ADJUSTER 가입은 로그인 불가(PENDING) → 데모용 활성화 경로 필요 (dev 프로파일 엔드포인트 or psql `update accounts set status='ACTIVE'`).

## 상태·TODO

- [x] 전 계층 GREEN
- [ ] 비밀번호 재설정 플로우
- [ ] 관리자 승인 엔드포인트 (ADJUSTER 활성화)
- [ ] RSA 키 외부화, RefreshTokenStore → Redis
