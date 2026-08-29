<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const loading = ref('')
const err = ref('')

async function enter(role) {
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
</script>

<template>
  <div class="center-screen">
    <div class="stack" style="width: 100%; max-width: 520px; align-items: center; text-align: center">
      <div class="brand-word" style="font-size: 28px">Insurance<b>AI</b></div>
      <p class="muted">AI 중립 청구 협업 공간 — 역할을 선택해 바로 시작하세요</p>

      <div class="role-grid">
        <button class="role-card" :disabled="loading" @click="enter('CLAIMANT')">
          <span class="emoji">🧾</span>
          <span class="role-name">청구인</span>
          <span class="small muted">보험금을 청구하고 서류를 제출합니다</span>
          <span class="go">{{ loading === 'CLAIMANT' ? '입장 중…' : '청구인으로 시작 →' }}</span>
        </button>

        <button class="role-card" :disabled="loading" @click="enter('ADJUSTER')">
          <span class="emoji">🔎</span>
          <span class="role-name">손해사정사</span>
          <span class="small muted">서류를 요청하고 검토·사정합니다</span>
          <span class="go">{{ loading === 'ADJUSTER' ? '입장 중…' : '손해사정사로 시작 →' }}</span>
        </button>
      </div>

      <div v-if="err" class="alert err" style="width: 100%">{{ err }}</div>
      <p class="small muted">로컬 데모 모드 — 로그인 없이 역할별 데모 계정으로 입장합니다.</p>
    </div>
  </div>
</template>

<style scoped>
.role-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  width: 100%;
  margin-top: 8px;
}
@media (max-width: 480px) {
  .role-grid {
    grid-template-columns: 1fr;
  }
}
.role-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 28px 18px;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  cursor: pointer;
  transition:
    box-shadow 0.15s,
    transform 0.15s,
    border-color 0.15s;
}
.role-card:hover:not(:disabled) {
  box-shadow: var(--shadow-hover);
  transform: translateY(-3px);
  border-color: var(--brand);
}
.role-card:disabled {
  opacity: 0.6;
  cursor: default;
}
.emoji {
  font-size: 34px;
}
.role-name {
  font-size: 18px;
  font-weight: 800;
}
.go {
  margin-top: 6px;
  color: var(--brand);
  font-weight: 700;
  font-size: 14px;
}
</style>
