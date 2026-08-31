<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const loading = ref('')
const err = ref('')

async function demo(role) {
  err.value = ''
  loading.value = role
  try {
    await auth.demoLogin(role)
    router.push({ name: 'cases' })
  } catch (e) {
    err.value = e.message || '입장에 실패했습니다.'
  } finally {
    loading.value = ''
  }
}

const features = [
  { icon: '📌', title: '청구 건별 관리', desc: '보험 청구를 건별로 만들고, 진행 상태를 한 곳에서 확인합니다.' },
  { icon: '📄', title: '요청·안내 피드', desc: '손해사정사의 서류 요청과 안내가 시간순으로 쌓여 놓치지 않습니다.' },
  { icon: '🤖', title: 'AI 중개', desc: '입력을 양식 칸에 맞춰 구조화하고 누락을 짚어 줍니다.' },
]
</script>

<template>
  <div class="home">
    <section class="hero">
      <div class="brand-word" style="font-size: 34px">Insurance<b>AI</b></div>
      <h1 class="tagline">보험금 청구, 청구인과 손해사정사가 한 화면에서</h1>
      <p class="muted">
        AI가 그 사이를 중개합니다. 서류 요청·제출·검토·사정을 하나의 협업 공간에서.
      </p>
      <div class="cta">
        <RouterLink v-if="!auth.isAuthenticated" class="btn" to="/login">로그인</RouterLink>
        <RouterLink v-if="!auth.isAuthenticated" class="btn ghost" to="/signup">회원가입</RouterLink>
        <RouterLink v-else class="btn" to="/cases">내 청구 관리로 →</RouterLink>
      </div>
    </section>

    <section class="grid" style="margin-top: 40px">
      <div v-for="f in features" :key="f.title" class="card stack" style="gap: 8px">
        <div style="font-size: 26px">{{ f.icon }}</div>
        <h3>{{ f.title }}</h3>
        <p class="muted small">{{ f.desc }}</p>
      </div>
    </section>

    <section class="demo">
      <div class="divider" />
      <p class="muted small">로그인 없이 둘러보기 — 데모 계정으로 역할을 체험합니다.</p>
      <div class="row" style="gap: 12px; flex-wrap: wrap">
        <button class="btn ghost" :disabled="loading" @click="demo('CLAIMANT')">
          {{ loading === 'CLAIMANT' ? '입장 중…' : '🧾 청구인으로 둘러보기' }}
        </button>
        <button class="btn ghost" :disabled="loading" @click="demo('ADJUSTER')">
          {{ loading === 'ADJUSTER' ? '입장 중…' : '🔎 손해사정사로 둘러보기' }}
        </button>
      </div>
      <div v-if="err" class="alert err" style="margin-top: 10px">{{ err }}</div>
    </section>
  </div>
</template>

<style scoped>
.home {
  max-width: 860px;
  margin: 0 auto;
}
.hero {
  text-align: center;
  padding: 40px 0 12px;
}
.tagline {
  margin: 18px 0 8px;
  font-size: 26px;
  line-height: 1.35;
}
.cta {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 22px;
  flex-wrap: wrap;
}
.demo {
  margin-top: 44px;
}
</style>
