<script setup>
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { listMyPolicies, seedDemoPolicies } from '@/api/policies'

const auth = useAuthStore()

const policies = ref([])
const loading = ref(true)
const err = ref('')

async function load() {
  loading.value = true
  err.value = ''
  try {
    policies.value = await listMyPolicies()
  } catch (e) {
    err.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function loadDemo() {
  err.value = ''
  try {
    await seedDemoPolicies()
    await load()
  } catch (e) {
    err.value = e.message
  }
}

const statusClass = {
  ACTIVE: 'b-ok',
  LAPSED: 'b-danger',
  EXPIRED: 'b-muted',
  TERMINATED: 'b-muted',
}

const won = (n) => (n == null ? '-' : Number(n).toLocaleString('ko-KR') + '원')
const fmtDate = (d) => (d ? d.replaceAll('-', '.') : '-')

const activeCount = computed(() => policies.value.filter((p) => p.status === 'ACTIVE').length)
</script>

<template>
  <div class="stack">
    <div class="between">
      <div class="stack" style="gap: 2px">
        <h1>내 보험</h1>
        <span class="small muted">
          가입한 생명보험 계약 {{ policies.length }}건 · 유효 {{ activeCount }}건
        </span>
      </div>
      <RouterLink to="/cases" class="btn ghost">청구 관리로 →</RouterLink>
    </div>

    <div v-if="err" class="alert err">{{ err }}</div>
    <p v-if="loading" class="muted">불러오는 중…</p>

    <template v-else>
      <div v-if="policies.length" class="grid">
        <div v-for="p in policies" :key="p.id" class="pol card stack" style="gap: 10px">
          <div class="between">
            <span class="chip on" style="cursor: default">{{ p.planTypeLabel }}</span>
            <span class="badge" :class="statusClass[p.status] || 'b-muted'">{{ p.statusLabel }}</span>
          </div>

          <div class="stack" style="gap: 2px">
            <h3>{{ p.productName }}</h3>
            <span class="small muted">{{ p.insurerName }} · 증권번호 {{ p.policyNumber }}</span>
          </div>

          <p v-if="p.coverageSummary" class="summary small">{{ p.coverageSummary }}</p>

          <dl class="kv small">
            <dt>피보험자</dt>
            <dd>{{ p.insuredName }}</dd>
            <dt>가입금액</dt>
            <dd>{{ won(p.coverageAmount) }}</dd>
            <dt>월 보험료</dt>
            <dd>{{ won(p.monthlyPremium) }}</dd>
            <dt>계약일</dt>
            <dd>{{ fmtDate(p.contractDate) }}</dd>
            <dt>보장기간</dt>
            <dd>{{ fmtDate(p.coverageStartDate) }} ~ {{ p.coverageEndDate ? fmtDate(p.coverageEndDate) : '종신' }}</dd>
          </dl>

          <RouterLink v-if="auth.isClaimant" to="/cases/new" class="btn sm block">
            이 보험으로 청구 시작
          </RouterLink>
        </div>
      </div>

      <div v-else class="card stack" style="gap: 12px">
        <p class="muted">조회된 보험 계약이 없습니다.</p>
        <button class="btn ghost" style="align-self: flex-start" @click="loadDemo">
          데모 보험 불러오기
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.pol {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 18px 20px;
}
.summary {
  background: var(--surface);
  border-radius: 8px;
  padding: 10px 12px;
  color: var(--ink-2);
  line-height: 1.5;
}
.kv {
  display: grid;
  grid-template-columns: 84px 1fr;
  gap: 6px 12px;
  margin: 0;
}
.kv dt {
  color: var(--muted);
}
.kv dd {
  margin: 0;
}
</style>
