<script setup>
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { assignAdjuster, listCases } from '@/api/cases'
import { claimTypeLabel } from '@/lib/claimType'
import StatusBadge from '@/components/StatusBadge.vue'

const auth = useAuthStore()

const mine = ref([])
const unassigned = ref([])
const loading = ref(true)
const err = ref('')
const query = ref('')

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

const fmt = (at) => (at ? new Date(at).toLocaleDateString('ko-KR') : '')

function matches(c) {
  const q = query.value.trim().toLowerCase()
  if (!q) return true
  return (
    (c.claimantName || '').toLowerCase().includes(q) ||
    (c.title || '').toLowerCase().includes(q) ||
    claimTypeLabel(c.claimType).toLowerCase().includes(q)
  )
}

// 청구 분야별 그룹핑 (사정사 화면)
function groupByType(list) {
  const groups = new Map()
  for (const c of list.filter(matches)) {
    const key = c.claimType || 'OTHER'
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(c)
  }
  return [...groups.entries()].map(([code, cases]) => ({ code, label: claimTypeLabel(code), cases }))
}

const myGroups = computed(() => groupByType(mine.value))
const unassignedGroups = computed(() => groupByType(unassigned.value))

async function takeCase(c) {
  try {
    await assignAdjuster(c.id, auth.user.id)
    await load()
  } catch (e) {
    err.value = e.message
  }
}
</script>

<template>
  <div class="stack">
    <div class="between">
      <div class="stack" style="gap: 2px">
        <h1>{{ auth.isAdjuster ? '담당 청구 관리' : '내 청구 목록' }}</h1>
        <span class="small muted">
          {{ auth.roles[0] === 'ADJUSTER' ? '손해사정사' : '청구인' }} · {{ auth.user?.id?.slice(0, 8) }}
        </span>
      </div>
      <RouterLink v-if="auth.isClaimant" to="/cases/new" class="btn">+ 새 청구 시작</RouterLink>
    </div>

    <div v-if="err" class="alert err">{{ err }}</div>
    <p v-if="loading" class="muted">불러오는 중…</p>

    <!-- 청구인 -->
    <template v-else-if="auth.isClaimant">
      <div v-if="mine.length" class="grid">
        <RouterLink v-for="c in mine" :key="c.id" :to="`/cases/${c.id}`" class="case-card card-link">
          <div class="between">
            <span class="chip on" style="cursor: default">{{ claimTypeLabel(c.claimType) }}</span>
            <span class="link-more">자세히 보기 →</span>
          </div>
          <h3 style="margin-top: 12px">{{ c.title }}</h3>
          <div class="row" style="margin-top: 6px; gap: 8px">
            <StatusBadge :status="c.status" />
            <span class="small muted">
              {{ c.adjusterName ? `담당 ${c.adjusterName}` : '사정사 미배정' }}
            </span>
          </div>
          <div class="divider" style="margin: 14px 0 10px" />
          <span class="small muted">개설일 {{ fmt(c.createdAt) }}</span>
        </RouterLink>
      </div>
      <div v-else class="card muted">아직 청구 건이 없습니다. “새 청구 시작”으로 개설하세요.</div>
    </template>

    <!-- 손해사정사 -->
    <template v-else>
      <input
        v-model="query"
        class="input"
        placeholder="청구인 이름 · 청구 개요 · 보험 분야로 검색"
      />

      <template v-if="mine.length">
        <div v-for="g in myGroups" :key="g.code" class="stack" style="gap: 10px">
          <h2>{{ g.label }} <span class="muted small">({{ g.cases.length }})</span></h2>
          <div class="grid">
            <RouterLink
              v-for="c in g.cases"
              :key="c.id"
              :to="`/cases/${c.id}`"
              class="case-card card-link"
            >
              <div class="between">
                <span class="claimant">{{ c.claimantName || '청구인' }}</span>
                <span class="link-more">→</span>
              </div>
              <h3 style="margin-top: 6px">{{ c.title }}</h3>
              <div class="row" style="margin-top: 8px; gap: 8px">
                <StatusBadge :status="c.status" />
                <span class="small muted">{{ fmt(c.createdAt) }}</span>
              </div>
            </RouterLink>
          </div>
        </div>
        <p v-if="!myGroups.length" class="card muted">검색 결과가 없습니다.</p>
      </template>
      <div v-else class="card muted">배정된 청구 건이 없습니다.</div>

      <div class="divider" />
      <h2>배정 대기 중인 청구</h2>
      <template v-if="unassigned.length">
        <div v-for="g in unassignedGroups" :key="g.code" class="stack" style="gap: 10px">
          <h3 class="muted">{{ g.label }} ({{ g.cases.length }})</h3>
          <div class="grid">
            <div v-for="c in g.cases" :key="c.id" class="case-card">
              <span class="claimant">{{ c.claimantName || '청구인' }}</span>
              <h3 style="margin-top: 6px">{{ c.title }}</h3>
              <div class="row" style="margin-top: 8px; gap: 8px">
                <StatusBadge :status="c.status" />
                <span class="small muted">{{ fmt(c.createdAt) }}</span>
              </div>
              <button class="btn sm block" style="margin-top: 12px" @click="takeCase(c)">
                이 청구 배정받기
              </button>
            </div>
          </div>
        </div>
      </template>
      <p v-else class="muted small">배정 대기 중인 청구가 없습니다.</p>
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
.claimant {
  font-weight: 800;
  font-size: 15px;
}
</style>
