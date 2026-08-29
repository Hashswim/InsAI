import { decodeJwt } from '@/lib/jwt'

const A_KEY = 'insai.accessToken'
const R_KEY = 'insai.refreshToken'

function load(k) {
  try {
    return localStorage.getItem(k)
  } catch {
    return null
  }
}
function store(k, v) {
  try {
    if (v == null) localStorage.removeItem(k)
    else localStorage.setItem(k, v)
  } catch {
    /* private mode 등 — 무시 */
  }
}

let accessToken = load(A_KEY)
let refreshToken = load(R_KEY)

/**
 * 토큰 원천. client.js(비반응) 와 stores/auth.js(반응 래퍼) 가 공유한다.
 * 순환 import 방지를 위해 여기서는 jwt 만 import.
 */
export const session = {
  get accessToken() {
    return accessToken
  },
  get refreshToken() {
    return refreshToken
  },
  get user() {
    if (!accessToken) return null
    const p = decodeJwt(accessToken)
    if (!p || !p.sub) return null
    return {
      id: p.sub,
      roles: (p.roles || []).map((r) => String(r).replace(/^ROLE_/, '')),
      exp: p.exp,
    }
  },
  set({ accessToken: a, refreshToken: r }) {
    accessToken = a || null
    refreshToken = r || null
    store(A_KEY, accessToken)
    store(R_KEY, refreshToken)
  },
  clear() {
    accessToken = null
    refreshToken = null
    store(A_KEY, null)
    store(R_KEY, null)
  },
}
