import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { session } from '@/api/session'
import * as authApi from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  // session 변경 후 user computed 를 재평가하기 위한 트리거
  const rev = ref(0)

  const user = computed(() => {
    rev.value // 의존성
    return session.user
  })
  const isAuthenticated = computed(() => !!user.value)
  const roles = computed(() => user.value?.roles ?? [])
  const isAdjuster = computed(() => roles.value.includes('ADJUSTER'))
  const isClaimant = computed(() => roles.value.includes('CLAIMANT'))

  async function login(email, password) {
    const tokens = await authApi.login(email, password)
    session.set(tokens)
    rev.value++
    return user.value
  }

  // 역할 버튼으로 바로 입장 (dev 데모)
  async function demoLogin(role) {
    const tokens = await authApi.demoLogin(role)
    session.set(tokens)
    rev.value++
    return user.value
  }

  async function signup(payload) {
    await authApi.signup(payload)
  }

  async function logout() {
    try {
      if (session.refreshToken) await authApi.logout(session.refreshToken)
    } catch {
      /* 무시 */
    }
    session.clear()
    rev.value++
  }

  return { user, isAuthenticated, roles, isAdjuster, isClaimant, login, demoLogin, signup, logout }
})
