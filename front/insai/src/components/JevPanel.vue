<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { listCaseFiles } from '@/api/files'
import { BUNDLES, KIND_LABELS, getLatestJev, runJev } from '@/api/jev'

// 청구인: 제출 서류를 점검하고 빠진 항목을 바로 안내받는다.
// 손해사정사: 서류가 갖춰진 건의 AI 요약·보완사항·의심사항을 보고 판단한다.
const props = defineProps({
  caseId: { type: String, required: true },
  role: { type: String, default: 'CLAIMANT' },
})

const JUDGMENTS = {
  SUITABLE: { label: '이상 없음', cls: 'b-ok', icon: '✓' },
  NEEDS_SUPPLEMENT: { label: '보완 필요', cls: 'b-warn', icon: '!' },
  UNVERIFIABLE: { label: '확인 불가', cls: 'b-muted', icon: '?' },
}
const LEVELS = {
  HIGH: { label: '높음', cls: 'b-danger' },
  MEDIUM: { label: '확인 필요', cls: 'b-warn' },
  LOW: { label: '낮음', cls: 'b-muted' },
}

const isAdjuster = computed(() => props.role === 'ADJUSTER')
const files = ref([])
const bundle = ref('OUTPATIENT')
const picks = ref({})
const running = ref(false)
const loaded = ref(false)
const err = ref('')
const analysis = ref(null)

const pdfs = computed(() => files.value.filter((f) => f.contentType === 'application/pdf'))
const kinds = computed(() => BUNDLES[bundle.value].kinds)
const ready = computed(() => {
  const ids = kinds.value.map((k) => picks.value[k]).filter(Boolean)
  return ids.length === 3 && new Set(ids).size === 3
})
const result = computed(() => analysis.value?.result)
// rules = 서류가 갖춰지지 않아 AI를 부르지 않고 청구인에게 바로 보완을 안내한 결과
const forwarded = computed(() => result.value && result.value.provider !== 'rules')
const gaps = computed(() => result.value?.items.filter((it) => it.judgment !== 'SUITABLE') ?? [])
const supplements = computed(
  () => result.value?.items.filter((it) => it.judgment === 'NEEDS_SUPPLEMENT') ?? [],
)
// 근거의 document_id 는 업로드한 파일 ID 다.
const fileName = (id) => files.value.find((f) => f.id === id)?.filename || '첨부 파일'

watch(bundle, () => (picks.value = {}))

onMounted(async () => {
  try {
    const [list, latest] = await Promise.all([listCaseFiles(props.caseId), getLatestJev(props.caseId)])
    files.value = list
    analysis.value = latest
  } catch (e) {
    err.value = e.message
  } finally {
    loaded.value = true
  }
})

async function run() {
  running.value = true
  err.value = ''
  try {
    const documents = kinds.value.map((kind) => ({ fileId: picks.value[kind], kind }))
    analysis.value = await runJev(props.caseId, documents)
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
      <h2>{{ isAdjuster ? 'AI 검토 요약' : '서류 점검' }}</h2>
      <span class="badge b-muted">최종 판정은 손해사정사가 합니다</span>
    </div>

    <!-- 서류 선택 · 점검 -->
    <div class="card stack">
      <p class="small muted">
        <template v-if="isAdjuster">
          청구인이 점검한 최근 결과가 아래에 표시됩니다. 필요하면 서류를 골라 다시 점검할 수 있습니다.
        </template>
        <template v-else>
          제출할 PDF를 종류별로 고르고 점검을 누르세요. 빠진 항목은 바로 알려 드리고, 모두 확인되면 담당
          손해사정사에게 전달됩니다. PDF 원본은 AI로 전송되지 않습니다.
        </template>
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
        {{ running ? '점검 중… (최대 2분)' : isAdjuster ? '다시 점검' : '점검하고 제출' }}
      </button>
      <div v-if="err" class="alert err">{{ err }}</div>
    </div>

    <p v-if="loaded && !result" class="card small muted">
      {{ isAdjuster ? '청구인이 아직 서류를 점검·제출하지 않았습니다.' : '아직 점검한 서류가 없습니다.' }}
    </p>

    <!-- 서류가 갖춰지지 않음: 청구인에게 바로 보완 안내 (AI 미호출) -->
    <template v-else-if="result && !forwarded">
      <div class="card stack">
        <div class="row" style="gap: 6px">
          <span class="badge b-warn">보완 필요</span>
          <span class="small muted">빠른 검사 결과 · AI 미호출</span>
        </div>
        <p v-if="isAdjuster" class="small">
          서류가 아직 완비되지 않아 청구인에게 보완 안내가 나갔습니다. 보완 후 다시 제출되면 AI 요약이 생성됩니다.
        </p>
        <p v-else>{{ result.summary }}</p>
        <ul class="list">
          <li v-for="(it, i) in gaps" :key="i">
            <b>{{ it.item }}</b>
            <span class="small"> — {{ it.supplement || it.verification_reason }}</span>
          </li>
        </ul>
      </div>
    </template>

    <!-- 서류가 갖춰짐: 청구인에게는 전달 완료만, 손해사정사에게는 AI 정리 -->
    <div v-else-if="result && !isAdjuster" class="alert ok">
      서류가 모두 확인되어 담당 손해사정사에게 전달되었습니다. 검토 결과는 “안내 사항”에서 알려 드립니다.
    </div>

    <template v-else-if="result">
      <div class="card stack summary">
        <div class="row" style="gap: 6px; flex-wrap: wrap">
          <span class="badge b-warn">판정 보류</span>
          <span class="badge b-info">AI 정리 · {{ result.provider }}</span>
          <span class="small muted">
            토큰 {{ (result.input_tokens + result.output_tokens).toLocaleString('ko-KR') }}
          </span>
        </div>
        <h3>요약</h3>
        <p>{{ result.summary }}</p>
        <p class="small muted">{{ result.reason }} 사람의 검토가 필요합니다.</p>
      </div>

      <div class="card stack">
        <h3>보완사항 {{ supplements.length }}건</h3>
        <p v-if="!supplements.length" class="small muted">AI가 제안한 보완사항이 없습니다.</p>
        <ul v-else class="list">
          <li v-for="(it, i) in supplements" :key="i">
            <b>{{ it.item }}</b>
            <span class="small"> — {{ it.supplement }}</span>
          </li>
        </ul>
        <p v-if="supplements.length" class="small muted">
          “서류 검토”에서 보완 요청을 누르면 이 내용이 요청 문구로 미리 채워집니다.
        </p>
      </div>

      <div class="card stack">
        <h3>의심사항 {{ result.risk_signals.length }}건</h3>
        <p v-if="!result.risk_signals.length" class="small muted">추가 확인이 필요한 신호가 없습니다.</p>
        <div v-for="(s, i) in result.risk_signals" :key="i" class="signal stack">
          <div class="between">
            <b>{{ s.signal }}</b>
            <span class="badge" :class="LEVELS[s.level].cls">{{ LEVELS[s.level].label }}</span>
          </div>
          <p class="small">{{ s.reason }}</p>
          <ul class="list small muted">
            <li v-for="(ev, j) in s.evidence" :key="j">
              {{ fileName(ev.document_id) }} {{ ev.page }}쪽 — {{ ev.description }}
            </li>
          </ul>
        </div>
        <p v-if="result.risk_signals.length" class="small muted">
          허위·과다청구로 단정한 것이 아니라 원본 확인이 필요한 지점입니다.
        </p>
      </div>

      <h3>항목별 점검</h3>
      <div v-for="(it, i) in result.items" :key="i" class="card stack">
        <div class="between">
          <b>{{ it.item }}</b>
          <span class="badge" :class="JUDGMENTS[it.judgment].cls">
            {{ JUDGMENTS[it.judgment].icon }} {{ JUDGMENTS[it.judgment].label }}
          </span>
        </div>
        <p class="small">{{ it.verification_reason }}</p>
        <div v-if="it.supplement" class="alert info small">보완 안내: {{ it.supplement }}</div>
        <ul v-if="it.evidence.length" class="list small muted">
          <li v-for="(ev, j) in it.evidence" :key="j">
            {{ fileName(ev.document_id) }} {{ ev.page }}쪽 — {{ ev.description }}
          </li>
        </ul>
        <p v-if="it.judgment !== 'UNVERIFIABLE'" class="small muted">
          AI 자기평가 신뢰도 {{ Math.round(it.confidence * 100) }}% (지급 승인 확률이 아닙니다)
        </p>
      </div>
    </template>
  </section>
</template>

<style scoped>
.summary {
  border-left: 4px solid var(--brand);
}
.list {
  margin: 0;
  padding-left: 18px;
}
.list li + li {
  margin-top: 6px;
}
.signal {
  gap: 4px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
}
</style>
