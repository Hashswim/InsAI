<script setup>
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { assignAdjuster, listCases } from '@/api/cases'
import StatusBadge from '@/components/StatusBadge.vue'

const auth = useAuthStore()

const mine = ref([])
const unassigned = ref([])
const loading = ref(true)
const err = ref('')

async function load() {
  loading.value = true
  err.value = ''
  try {
    mine.value = await listCases(false)
    if (auth.isAdjuster) unassigned.value = await listCases(true)
  } catch (e) {
    err.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)

function myRole(c) {
  if (c.claimantId === auth.user?.id) return '청구인'
  if (c.adjusterId === auth.user?.id) return '담당 사정사'
  return null
}
const fmt = (at) => (at ? new Date(at).toLocaleDateString('ko-KR') : '')

async function takeCase(c) {
  try {
    await assignAdjuster(c.id, auth.user.id)
    await load()
  } catch (e) {
    err.value = e.message
  }
}

const heading = computed(() => (auth.isAdjuster ? '담당 청구 건' : '내 청구 목록'))
</script>

<template>
  <div class="stack">
    <div class="between">
      <div class="stack" style="gap: 2px">
        <h1>{{ heading }}</h1>
        <span class="small muted">계정 {{ auth.user?.id?.slice(0, 8) }} · {{ auth.roles[0] === 'ADJUSTER' ? '손해사정사' : '청구인' }}</span>
      </div>
      <RouterLink v-if="auth.isClaimant" to="/cases/new" class="btn">+ 새 청구 시작</RouterLink>
    </div>

    <div v-if="err" class="alert err">{{ err }}</div>
    <p v-if="loading" class="muted">불러오는 중…</p>

    <template v-else>
      <div v-if="mine.length" class="grid">
        <RouterLink v-for="c in mine" :key="c.id" :to="`/cases/${c.id}`" class="case-card card-link">
          <div class="between">
            <span class="chip on" style="cursor: default">보험금 청구</span>
            <span class="link-more">자세히 보기 →</span>
          </div>
          <h3 style="margin-top: 12px">{{ c.title }}</h3>
          <div class="row" style="margin-top: 6px; gap: 8px">
            <StatusBadge :status="c.status" />
            <span v-if="myRole(c)" class="small muted">{{ myRole(c) }}</span>
          </div>
          <div class="divider" style="margin: 14px 0 10px" />
          <span class="small muted">개설일 {{ fmt(c.createdAt) }}</span>
        </RouterLink>
      </div>
      <div v-else class="card muted">
        {{
          auth.isClaimant
            ? '아직 청구 건이 없습니다. “새 청구 시작”으로 개설하세요.'
            : '배정된 청구 건이 없습니다.'
        }}
      </div>

      <template v-if="auth.isAdjuster">
        <div class="divider" />
        <h2>배정 대기 중인 청구</h2>
        <div v-if="unassigned.length" class="grid">
          <div v-for="c in unassigned" :key="c.id" class="case-card">
            <span class="chip on" style="cursor: default">보험금 청구</span>
            <h3 style="margin-top: 12px">{{ c.title }}</h3>
            <div class="row" style="margin-top: 6px; gap: 8px">
              <StatusBadge :status="c.status" />
              <span class="small muted">개설 {{ fmt(c.createdAt) }}</span>
            </div>
            <button class="btn sm block" style="margin-top: 14px" @click="takeCase(c)">
              이 청구 배정받기
            </button>
          </div>
        </div>
        <p v-else class="muted small">배정 대기 중인 청구가 없습니다.</p>
      </template>
    </template>
  </div>
</template>

<style scoped>
.case-card {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 18px 20px;
}
.link-more {
  color: var(--brand);
  font-weight: 700;
  font-size: 13px;
}
</style>
