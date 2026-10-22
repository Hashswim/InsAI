<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { listCases } from '@/api/cases'
import { jevMetrics } from '@/api/jev'

const RANGES = [
  { days: 1, label: '오늘' },
  { days: 7, label: '7일' },
  { days: 31, label: '31일' },
]
const MODEL_PROVIDERS = ['gemini', 'openai', 'stub']

const days = ref(7)
const rows = ref([])
const loading = ref(false)
const err = ref('')

const num = (n) => Number(n || 0).toLocaleString('ko-KR')
const modelCalls = (m) => MODEL_PROVIDERS.reduce((sum, p) => sum + (m.providers?.[p] || 0), 0)

async function load() {
  loading.value = true
  err.value = ''
  try {
    const cases = await listCases()
    // 지표 API는 케이스 단위로 권한을 확인하므로 케이스마다 조회해 합산한다.
    const results = await Promise.allSettled(cases.map((c) => jevMetrics(c.id, days.value)))
    rows.value = cases
      .map((c, i) => ({ c, m: results[i].status === 'fulfilled' ? results[i].value : null }))
      .filter((r) => r.m)
  } catch (e) {
    err.value = e.message
  } finally {
    loading.value = false
  }
}

const total = computed(() => {
  const t = { analyses: 0, rules: 0, model: 0, input: 0, output: 0, failed: 0, jobs: {} }
  for (const { m } of rows.value) {
    t.analyses += m.storedAnalyses
    t.rules += m.providers?.rules || 0
    t.model += modelCalls(m)
    t.input += m.inputTokens
    t.output += m.outputTokens
    t.failed += (m.jobStatuses?.FAILED || 0) + (m.jobStatuses?.UNKNOWN || 0)
    for (const [k, v] of Object.entries(m.jobStatuses || {})) t.jobs[k] = (t.jobs[k] || 0) + v
  }
  return t
})
const ruleShare = computed(() => {
  const known = total.value.rules + total.value.model
  return known ? Math.round((total.value.rules / known) * 100) : null
})
const avgTokens = computed(() =>
  total.value.model ? Math.round((total.value.input + total.value.output) / total.value.model) : 0,
)

watch(days, load)
onMounted(load)
</script>

<template>
  <div class="stack">
    <div class="between">
      <div>
        <h1>AI 점검 대시보드</h1>
        <p class="small muted">내가 참여한 청구 건의 JEV 서류 점검 현황</p>
      </div>
      <div class="tabs">
        <button v-for="r in RANGES" :key="r.days" :class="{ on: days === r.days }" @click="days = r.days">
          {{ r.label }}
        </button>
      </div>
    </div>
    <div v-if="err" class="alert err">{{ err }}</div>

    <div class="tiles">
      <div class="card tile">
        <div class="small muted">전체 분석</div>
        <div class="hero">{{ num(total.analyses) }}<span>건</span></div>
      </div>
      <div class="card tile">
        <div class="small muted">청구인 바로 보완 안내 (AI 미호출)</div>
        <div class="hero">{{ ruleShare === null ? '–' : ruleShare }}<span v-if="ruleShare !== null">%</span></div>
        <div
          class="meter"
          role="img"
          :aria-label="`보완 안내 ${total.rules}건, AI 정리 ${total.model}건`"
        >
          <div class="meter-fill" :style="{ width: (ruleShare || 0) + '%' }" />
        </div>
        <div class="small muted">보완 안내 {{ num(total.rules) }}건 · AI 정리 {{ num(total.model) }}건</div>
      </div>
      <div class="card tile">
        <div class="small muted">사용 토큰 (입력 + 출력)</div>
        <div class="hero">{{ num(total.input + total.output) }}</div>
        <div class="small muted">AI 호출 1건당 평균 {{ num(avgTokens) }}</div>
      </div>
      <div class="card tile">
        <div class="small muted">실패·중단 작업</div>
        <div class="hero">{{ num(total.failed) }}<span>건</span></div>
        <div class="small muted">비동기 작업 기준</div>
      </div>
    </div>

    <div class="card">
      <div class="between" style="margin-bottom: 10px">
        <h3>청구 건별 현황</h3>
        <span v-if="loading" class="small muted">불러오는 중…</span>
      </div>
      <p v-if="!loading && !rows.length" class="small muted">조회할 수 있는 청구 건이 없습니다.</p>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>청구 건</th>
              <th class="n">분석</th>
              <th class="n">보완 안내</th>
              <th class="n">AI 호출</th>
              <th class="n">토큰</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="{ c, m } in rows" :key="c.id">
              <td>
                <RouterLink :to="`/cases/${c.id}`" class="link">{{ c.title }}</RouterLink>
              </td>
              <td class="n">{{ num(m.storedAnalyses) }}</td>
              <td class="n">{{ num(m.providers?.rules) }}</td>
              <td class="n">{{ num(modelCalls(m)) }}</td>
              <td class="n">{{ num(m.inputTokens + m.outputTokens) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <p class="small muted">
      빠른 검사에서 누락·판독 불가·서류 종류 오류가 보이면 AI를 부르지 않고 청구인에게 바로 보완을
      안내합니다. 서류가 모두 갖춰진 건만 추출한 항목을 AI 에이전트 팀에 보내 손해사정사용 요약을 만듭니다.
    </p>
  </div>
</template>

<style scoped>
.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}
.tile {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.hero {
  font-size: 30px;
  font-weight: 700;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}
.hero span {
  font-size: 15px;
  font-weight: 600;
  color: var(--muted);
  margin-left: 2px;
}
.meter {
  height: 8px;
  border-radius: 4px;
  background: var(--border);
  overflow: hidden;
}
.meter-fill {
  height: 100%;
  border-radius: 4px;
  background: var(--brand);
}
.table-wrap {
  overflow-x: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}
th,
td {
  padding: 8px 6px;
  border-bottom: 1px solid var(--border);
  text-align: left;
}
th {
  color: var(--muted);
  font-weight: 600;
  font-size: 13px;
}
.n {
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.link {
  color: var(--brand-deep);
  font-weight: 600;
}
</style>
