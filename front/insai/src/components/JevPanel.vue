<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { listCaseFiles } from '@/api/files'
import { BUNDLES, KIND_LABELS, getJev, runJev } from '@/api/jev'

const props = defineProps({ caseId: { type: String, required: true } })

const JUDGMENTS = {
  SUITABLE: { label: '이상 없음', cls: 'b-ok', icon: '✓' },
  NEEDS_SUPPLEMENT: { label: '보완 필요', cls: 'b-warn', icon: '!' },
  UNVERIFIABLE: { label: '확인 불가', cls: 'b-muted', icon: '?' },
}

const files = ref([])
const bundle = ref('OUTPATIENT')
const picks = ref({})
const running = ref(false)
const err = ref('')
const analysis = ref(null)
// 마지막 분석 ID만 브라우저에 기억 (결과 본문은 서버에서 다시 읽는다)
const storageKey = `jev:last:${props.caseId}`

const pdfs = computed(() => files.value.filter((f) => f.contentType === 'application/pdf'))
const kinds = computed(() => BUNDLES[bundle.value].kinds)
const ready = computed(() => {
  const ids = kinds.value.map((k) => picks.value[k]).filter(Boolean)
  return ids.length === 3 && new Set(ids).size === 3
})
const result = computed(() => analysis.value?.result)
const byRules = computed(() => result.value?.provider === 'rules')
const fileName = (id) => files.value.find((f) => f.id === id)?.filename || '알 수 없는 파일'
const kindOf = (documentId) =>
  KIND_LABELS[Object.entries(lastPicks.value).find(([, id]) => id === documentId)?.[0]] ||
  fileName(documentId)
const lastPicks = ref({})

watch(bundle, () => (picks.value = {}))

onMounted(async () => {
  try {
    files.value = await listCaseFiles(props.caseId)
    const last = localStorage.getItem(storageKey)
    if (last) {
      const saved = JSON.parse(last)
      lastPicks.value = saved.picks || {}
      analysis.value = await getJev(saved.analysisId)
    }
  } catch {
    /* 이전 결과가 없거나 권한이 바뀐 경우 새로 분석한다 */
  }
})

async function run() {
  running.value = true
  err.value = ''
  try {
    const documents = kinds.value.map((kind) => ({ fileId: picks.value[kind], kind }))
    analysis.value = await runJev(props.caseId, documents)
    lastPicks.value = { ...picks.value }
    try {
      localStorage.setItem(
        storageKey,
        JSON.stringify({ analysisId: analysis.value.analysisId, picks: lastPicks.value }),
      )
    } catch {
      /* 저장소를 쓸 수 없어도 결과 표시는 계속한다 */
    }
  } catch (e) {
    err.value = e.message
  } finally {
    running.value = false
  }
}
</script>

<template>
  <section class="stack">
    <div class="between">
      <h2>AI 서류 점검</h2>
      <span class="badge b-muted">최종 판정은 손해사정사가 합니다</span>
    </div>

    <div class="card stack">
      <p class="small muted">
        PDF에서 진단명·질병코드·날짜·병원명·금액을 읽어 기재 상태를 점검합니다. 규칙으로 확인되면
        AI를 호출하지 않으며, PDF 원본은 AI로 전송되지 않습니다.
      </p>
      <div class="field">
        <label>서류 묶음</label>
        <div class="row" style="gap: 6px; flex-wrap: wrap">
          <button
            v-for="(b, key) in BUNDLES"
            :key="key"
            class="chip"
            :class="{ on: bundle === key }"
            @click="bundle = key"
          >
            {{ b.label }}
          </button>
        </div>
      </div>
      <p v-if="!pdfs.length" class="small muted">이 청구 건에 올라온 PDF가 없습니다.</p>
      <div v-for="kind in kinds" v-else :key="kind" class="field">
        <label>{{ KIND_LABELS[kind] }}</label>
        <select v-model="picks[kind]" class="select">
          <option :value="undefined" disabled>PDF 선택</option>
          <option v-for="f in pdfs" :key="f.id" :value="f.id">{{ f.filename }}</option>
        </select>
      </div>
      <p v-if="Object.keys(picks).length === 3 && !ready" class="small" style="color: var(--danger)">
        서류마다 서로 다른 파일을 골라 주세요.
      </p>
      <button class="btn" :disabled="!ready || running" @click="run">
        {{ running ? '점검 중… (최대 2분)' : '점검 시작' }}
      </button>
      <div v-if="err" class="alert err">{{ err }}</div>
    </div>

    <template v-if="result">
      <div class="card stack">
        <div class="row" style="gap: 6px; flex-wrap: wrap">
          <span class="badge b-warn">판정 보류</span>
          <span class="badge" :class="byRules ? 'b-brand' : 'b-info'">
            {{ byRules ? '규칙 점검으로 처리 · AI 호출 없음' : `AI 분석 · ${result.provider}` }}
          </span>
          <span v-if="!byRules" class="small muted">
            토큰 {{ (result.input_tokens + result.output_tokens).toLocaleString('ko-KR') }}
          </span>
        </div>
        <p class="small muted">{{ result.reason }} 사람의 검토가 필요합니다.</p>
      </div>

      <div v-for="(it, i) in result.items" :key="i" class="card stack item">
        <div class="between">
          <h3>{{ it.item }}</h3>
          <span class="badge" :class="JUDGMENTS[it.judgment].cls">
            {{ JUDGMENTS[it.judgment].icon }} {{ JUDGMENTS[it.judgment].label }}
          </span>
        </div>
        <p class="small">{{ it.verification_reason }}</p>
        <div v-if="it.supplement" class="alert info small">보완 안내: {{ it.supplement }}</div>
        <ul v-if="it.evidence.length" class="evidence small muted">
          <li v-for="(ev, j) in it.evidence" :key="j">
            {{ kindOf(ev.document_id) }} {{ ev.page }}쪽 — {{ ev.description }}
          </li>
        </ul>
        <p v-if="!byRules && it.judgment !== 'UNVERIFIABLE'" class="small muted">
          AI 자기평가 신뢰도 {{ Math.round(it.confidence * 100) }}% (지급 승인 확률이 아닙니다)
        </p>
      </div>
    </template>
  </section>
</template>

<style scoped>
.item h3 {
  font-size: 15px;
}
.evidence {
  margin: 0;
  padding-left: 18px;
}
</style>
