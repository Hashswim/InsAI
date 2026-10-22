# 기능: 인증 (회원가입 · 로그인 · 세션)

## 관통 모듈

`auth` (전부) · 프론트 `stores/auth`, `api/session`, `api/client`.

## 흐름

```
회원가입:  POST /auth/signup {email, password, name, role?}
           → CLAIMANT: ACTIVE, 바로 로그인 가능
           → ADJUSTER: PENDING, 관리자 승인(activate) 후 로그인
           → 응답 {success:true} (accountId 없음)

로그인:    POST /auth/login {email, password}
           → {accessToken(RS256,15분), refreshToken(불투명,7일), expiresIn}
           → 실패: 401(자격), 403(PENDING/SUSPENDED)

내 식별자: accessToken 디코드 → sub = accountId, roles = ["ROLE_CLAIMANT"]
           프론트가 이후 요청에 X-User-Id: <sub>, Authorization: Bearer <accessToken>

갱신:      POST /auth/refresh {refreshToken}  (401 시 프론트가 1회 자동)
           → 새 토큰쌍, 기존 refresh 즉시 폐기(회전). 재사용 시 401

로그아웃:  POST /auth/logout {refreshToken}  (멱등)
```

## 규칙

- 이메일 소문자·trim 정규화. 중복 → 409.
- 비밀번호 8~100자 (DTO 검증) + BCrypt 해시.
- refresh 회전: 갱신할 때마다 새 refresh, 이전 것 무효.

## 검증된 curl

```bash
curl -s localhost:8080/auth/signup -H 'Content-Type: application/json' \
  -d '{"email":"a@b.com","password":"password123","name":"홍길동"}'
TOKENS=$(curl -s localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"a@b.com","password":"password123"}')
# → {"success":true,"data":{"accessToken":"eyJ...","refreshToken":"...","expiresIn":899}}
```

## 알려진 갭

- ADJUSTER 활성화 경로 없음 → 데모용: `psql ... "update accounts set status='ACTIVE' where email='...'"` 또는 dev 엔드포인트 추가.
- 비밀번호 재설정 미구현.
- 게이트웨이 없음 → 현재 `X-User-Id` 는 클라이언트가 자칭. 신뢰 경계는 게이트웨이 도입 시 확립.

## 관련 테스트

`auth/**` 44개. 특히 `AuthFlowIntegrationTest` 가 전 시나리오(가입→로그인→갱신→로그아웃→폐기토큰 401, 잘못된 비번 401, ADJUSTER 승인 전 403).
