<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const email = ref('')
const password = ref('')
const err = ref('')
const loading = ref(false)

async function onSubmit() {
  err.value = ''
  loading.value = true
  try {
    await auth.login(email.value.trim(), password.value)
    router.push(route.query.redirect || { name: 'cases' })
  } catch (e) {
    err.value =
      e.status === 403
        ? '승인 대기 중인 계정입니다. 손해사정사는 관리자 승인 후 로그인할 수 있습니다.'
        : e.message || '로그인에 실패했습니다.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="center-screen">
    <div class="card auth-card stack">
      <div class="stack" style="gap: 4px">
        <div class="brand-word">Insurance<b>AI</b></div>
        <p class="muted small">AI 중립 청구 협업 공간</p>
      </div>

      <form class="stack" @submit.prevent="onSubmit">
        <div class="field">
          <label>이메일</label>
          <input v-model="email" class="input" type="email" autocomplete="email" required />
        </div>
        <div class="field">
          <label>비밀번호</label>
          <input
            v-model="password"
            class="input"
            type="password"
            autocomplete="current-password"
            required
          />
        </div>

        <div v-if="err" class="alert err">{{ err }}</div>

        <button class="btn block" type="submit" :disabled="loading">
          {{ loading ? '로그인 중…' : '로그인' }}
        </button>
      </form>

      <p class="small muted" style="text-align: center">
        계정이 없으신가요?
        <RouterLink to="/signup" style="color: var(--brand); font-weight: 700">회원가입</RouterLink>
      </p>
    </div>
  </div>
</template>
