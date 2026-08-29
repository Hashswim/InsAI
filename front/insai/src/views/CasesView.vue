<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { assignAdjuster, listCases } from '@/api/cases'
import StatusBadge from '@/components/StatusBadge.vue'

const auth = useAuthStore()
const router = useRouter()

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
function fmt(at) {
  return at ? new Date(at).toLocaleDateString('ko-KR') : ''
}

async function takeCase(c) {
  try {
    await assignAdjuster(c.id, auth.user.id)
    await load()
  } catch (e) {
    err.value = e.message
  }
}

const heading = computed(() => (auth.isAdjuster ? '담당 청구 건' : '내 청구 건'))
</script>

<template>
  <div class="stack">
    <div class="between">
      <h1>{{ heading }}</h1>
      <RouterLink v-if="auth.isClaimant" to="/cases/new" class="btn">+ 새 청구 시작</RouterLink>
    </div>

    <div v-if="err" class="alert err">{{ err }}</div>
    <p v-if="loading" class="muted">불러오는 중…</p>

    <template v-else>
      <div v-if="mine.length" class="grid">
        <RouterLink
          v-for="c in mine"
          :key="c.id"
          :to="`/cases/${c.id}`"
          class="card card-link stack"
          style="gap: 10px"
        >
          <div class="between">
            <StatusBadge :status="c.status" />
            <span v-if="myRole(c)" class="badge b-muted">{{ myRole(c) }}</span>
          </div>
          <h3>{{ c.title }}</h3>
          <span class="small muted">개설 {{ fmt(c.createdAt) }}</span>
        </RouterLink>
      </div>
      <div v-else class="card muted">
        {{ auth.isClaimant ? '아직 청구 건이 없습니다. “새 청구 시작”으로 개설하세요.' : '배정된 청구 건이 없습니다.' }}
      </div>

      <template v-if="auth.isAdjuster">
        <div class="divider" />
        <h2>배정 대기 중인 청구</h2>
        <div v-if="unassigned.length" class="grid">
          <div v-for="c in unassigned" :key="c.id" class="card stack" style="gap: 10px">
            <StatusBadge :status="c.status" />
            <h3>{{ c.title }}</h3>
            <span class="small muted">개설 {{ fmt(c.createdAt) }}</span>
            <button class="btn sm" @click="takeCase(c)">이 청구 배정받기</button>
          </div>
        </div>
        <p v-else class="muted small">배정 대기 중인 청구가 없습니다.</p>
      </template>
    </template>
  </div>
</template>
