<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { devActivate } from '@/api/auth'

const auth = useAuthStore()
const router = useRouter()

const name = ref('')
const email = ref('')
const password = ref('')
const role = ref('CLAIMANT')
const err = ref('')
const loading = ref(false)
const pendingAdjuster = ref(false)

async function onSubmit() {
  err.value = ''
  loading.value = true
  try {
    await auth.signup({
      name: name.value.trim(),
      email: email.value.trim(),
      password: password.value,
      role: role.value,
    })
    if (role.value === 'CLAIMANT') {
      await auth.login(email.value.trim(), password.value)
      router.push({ name: 'cases' })
    } else {
      pendingAdjuster.value = true
    }
  } catch (e) {
    err.value = e.message || '회원가입에 실패했습니다.'
  } finally {
    loading.value = false
  }
}

// 로컬 데모: 백엔드가 dev 프로파일이면 승인 대기 사정사를 즉시 활성화 후 로그인
async function demoActivate() {
  err.value = ''
  loading.value = true
  try {
    await devActivate(email.value.trim())
    await auth.login(email.value.trim(), password.value)
    router.push({ name: 'cases' })
  } catch (e) {
    err.value = e.message || '활성화에 실패했습니다. (백엔드 dev 프로파일에서만 동작)'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="center-screen">
    <div class="card auth-card stack">
      <div class="stack" style="gap: 4px">
        <div class="brand-word">회원가입</div>
        <p class="muted small">Insurance<b style="color: var(--brand)">AI</b></p>
      </div>

      <template v-if="!pendingAdjuster">
        <form class="stack" @submit.prevent="onSubmit">
          <div class="field">
            <label>이름</label>
            <input v-model="name" class="input" required />
          </div>
          <div class="field">
            <label>이메일</label>
            <input v-model="email" class="input" type="email" autocomplete="email" required />
          </div>
          <div class="field">
            <label>비밀번호 <span class="req">*</span></label>
            <input
              v-model="password"
              class="input"
              type="password"
              autocomplete="new-password"
              minlength="8"
              required
            />
            <span class="small muted">8자 이상</span>
          </div>
          <div class="field">
            <label>가입 유형</label>
            <div class="row">
              <button
                type="button"
                class="chip"
                :class="{ on: role === 'CLAIMANT' }"
                @click="role = 'CLAIMANT'"
              >
                청구인
              </button>
              <button
                type="button"
                class="chip"
                :class="{ on: role === 'ADJUSTER' }"
                @click="role = 'ADJUSTER'"
              >
                손해사정사
              </button>
            </div>
          </div>

          <div v-if="err" class="alert err">{{ err }}</div>

          <button class="btn block" type="submit" :disabled="loading">
            {{ loading ? '처리 중…' : '가입하기' }}
          </button>
        </form>

        <p class="small muted" style="text-align: center">
          이미 계정이 있으신가요?
          <RouterLink to="/login" style="color: var(--brand); font-weight: 700">로그인</RouterLink>
        </p>
      </template>

      <template v-else>
        <div class="alert ok">
          가입이 완료되었습니다. 손해사정사 계정은 <b>관리자 승인 후</b> 로그인할 수 있습니다.
        </div>
        <button class="btn ghost block" :disabled="loading" @click="demoActivate">
          로컬 데모용 즉시 활성화 후 로그인
        </button>
        <div v-if="err" class="alert err">{{ err }}</div>
        <RouterLink to="/login" class="small muted" style="text-align: center">로그인으로</RouterLink>
      </template>
    </div>
  </div>
</template>
