<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const roleLabel = { CLAIMANT: '청구인', ADJUSTER: '손해사정사', ADMIN: '운영자' }

async function onLogout() {
  await auth.logout()
  router.push({ name: 'landing' })
}
</script>

<template>
  <header class="hdr">
    <div class="hdr-inner">
      <RouterLink to="/cases" class="brand-word">Insurance<b>AI</b></RouterLink>
      <div class="grow" />
      <span class="badge b-brand">
        {{ (auth.roles[0] && roleLabel[auth.roles[0]]) || '사용자' }}
      </span>
      <span class="small muted">{{ auth.user?.id?.slice(0, 8) }}</span>
      <button class="btn ghost sm" @click="onLogout">나가기</button>
    </div>
  </header>
</template>

<style scoped>
.hdr {
  position: sticky;
  top: 0;
  z-index: 20;
  background: #fff;
  border-bottom: 1px solid var(--border);
}
.hdr-inner {
  max-width: 1000px;
  margin: 0 auto;
  height: 56px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 10px;
}
</style>
