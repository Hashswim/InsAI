// JWT payload 디코드 (검증 X — sub/roles/exp 추출용). 서명 검증은 게이트웨이 책임.
export function decodeJwt(token) {
  try {
    const part = token.split('.')[1]
    const b64 = part.replace(/-/g, '+').replace(/_/g, '/')
    const bin = atob(b64)
    const utf8 = decodeURIComponent(
      bin
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join(''),
    )
    return JSON.parse(utf8)
  } catch {
    return null
  }
}
