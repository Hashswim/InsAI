<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

// 테스트 전용: 백엔드 dev 프로파일의 /dev/demo-login 으로 자격 증명 없이 입장한다.
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const role = ref('CLAIMANT')
const loading = ref(false)
const err = ref('')

async function enter() {
  if (loading.value) return
  err.value = ''
  loading.value = true
  try {
    await auth.demoLogin(role.value)
    router.push(route.query.redirect || { name: 'cases' })
  } catch (e) {
    err.value = e.message || '입장에 실패했습니다. 백엔드가 dev 프로파일로 실행 중인지 확인하세요.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="quick stack">
    <div class="between">
      <h3>빠른 시작</h3>
      <span class="badge b-warn">테스트 전용</span>
    </div>
    <p class="small muted">데모 계정으로 바로 입장합니다. 개발 환경에서만 표시됩니다.</p>
    <div class="roles">
      <button type="button" :class="['role', { on: role === 'CLAIMANT' }]" @click="role = 'CLAIMANT'">
        <b>청구인</b><span class="small muted">보험금을 청구합니다</span>
      </button>
      <button type="button" :class="['role', { on: role === 'ADJUSTER' }]" @click="role = 'ADJUSTER'">
        <b>손해사정사</b><span class="small muted">청구를 검토·사정합니다</span>
      </button>
    </div>
    <div v-if="err" class="alert err">{{ err }}</div>
    <button type="button" class="btn ghost block" :disabled="loading" @click="enter">
      {{ loading ? '입장 중…' : role === 'ADJUSTER' ? '손해사정사로 입장' : '청구인으로 입장' }}
    </button>
  </div>
</template>

<style scoped>
.quick {
  gap: 10px;
  padding-top: 16px;
  border-top: 1px dashed var(--border);
}
.roles {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.role {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: flex-start;
  text-align: left;
  padding: 10px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--surface);
  cursor: pointer;
  font-family: inherit;
}
.role:hover,
.role.on {
  border-color: var(--brand);
}
.role.on {
  background: var(--brand-tint);
}
.role b {
  font-size: 14px;
}
</style>
