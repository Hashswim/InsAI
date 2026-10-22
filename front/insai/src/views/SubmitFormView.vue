<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import * as formsApi from '@/api/forms'
import FormFieldInput from '@/components/FormFieldInput.vue'

const props = defineProps({
  id: { type: String, required: true }, // caseId
  docRequestId: { type: String, required: true },
})
const route = useRoute()

const templateId = route.query.templateId
const template = ref(null)
const data = reactive({})
const err = ref('')
const loading = ref(true)
const saved = ref(false)
const step = ref('fill') // fill | confirm | done
const result = ref(null)

onMounted(async () => {
  try {
    const requested = await formsApi.getTemplate(templateId)
    // 요청 뒤에 서류 양식이 바뀌었으면(예: 입력 칸 → 첨부 전용) 최신 버전으로 받는다.
    const latest = await formsApi.getTemplateByCode(requested.code).catch(() => requested)
    template.value = latest.version > requested.version ? latest : requested
    for (const f of template.value.fields) data[f.key] = ''
    try {
      const prev = await formsApi.getSubmissionByDocRequest(props.docRequestId)
      if (prev?.data) for (const [k, v] of Object.entries(prev.data)) if (k in data) data[k] = String(v)
    } catch {
      /* 없으면 무시 */
    }
  } catch (e) {
    err.value = e.message
  } finally {
    loading.value = false
  }
})

const fields = computed(() => template.value?.fields ?? [])
const requiredFields = computed(() => fields.value.filter((f) => f.required))
function isFilled(f) {
  const v = data[f.key]
  return v != null && String(v).trim() !== ''
}
const filledRequired = computed(() => requiredFields.value.filter(isFilled).length)
const missingRequired = computed(() => requiredFields.value.filter((f) => !isFilled(f)))

function payload() {
  const clean = {}
  for (const [k, v] of Object.entries(data)) if (v !== '' && v != null) clean[k] = v
  return { documentRequestId: props.docRequestId, caseId: props.id, templateId: template.value.id, data: clean }
}

async function onSaveDraft() {
  err.value = ''
  saved.value = false
  try {
    await formsApi.saveDraft(payload())
    saved.value = true
  } catch (e) {
    err.value = e.message
  }
}

function toConfirm() {
  err.value = ''
  step.value = 'confirm'
  window.scrollTo({ top: 0 })
}

async function onSubmit() {
  err.value = ''
  try {
    await formsApi.submitForm(payload())
    result.value = await formsApi.getSubmissionByDocRequest(props.docRequestId)
    step.value = 'done'
    window.scrollTo({ top: 0 })
  } catch (e) {
    err.value = e.message
  }
}
</script>

<template>
  <div class="stack" style="max-width: 640px">
    <RouterLink :to="`/cases/${id}`" class="small muted">← 청구 상세로</RouterLink>
    <p v-if="loading" class="muted">불러오는 중…</p>
    <div v-if="err" class="alert err">{{ err }}</div>

    <template v-if="template">
      <div class="between">
        <h1>{{ template.name }}</h1>
        <div class="steps small muted">
          <span :class="{ on: step === 'fill' }">작성</span> ›
          <span :class="{ on: step === 'confirm' }">확인</span> ›
          <span :class="{ on: step === 'done' }">완료</span>
        </div>
      </div>

      <!-- 1. 작성 -->
      <template v-if="step === 'fill'">
        <div class="card stack" style="gap: 4px">
          <div class="between small">
            <span class="muted">필수 서류 첨부</span>
            <span :class="filledRequired === requiredFields.length ? 'ok-text' : 'muted'">
              {{ filledRequired }} / {{ requiredFields.length }}
            </span>
          </div>
          <div class="bar">
            <div
              class="bar-fill"
              :style="{ width: requiredFields.length ? (filledRequired / requiredFields.length) * 100 + '%' : '0%' }"
            />
          </div>
        </div>

        <p class="muted small">
          서류 PDF 파일만 올려 주세요. 질병코드·날짜·금액 같은 내용은 AI가 파일에서 직접 읽습니다. 궁금한 점은 문의 사항에 적어 주세요.
        </p>

        <form class="card stack" @submit.prevent="toConfirm">
          <FormFieldInput
            v-for="f in fields"
            :key="f.key"
            :field="f"
            :case-id="id"
            v-model="data[f.key]"
          />
          <div v-if="saved" class="alert ok">임시저장했습니다.</div>
          <div class="row">
            <button type="button" class="btn ghost" @click="onSaveDraft">임시저장</button>
            <button type="submit" class="btn">확인</button>
          </div>
        </form>
      </template>

      <!-- 2. 확인 -->
      <template v-else-if="step === 'confirm'">
        <p class="muted small">아래 내용으로 제출합니다. 손해사정사가 이 내용을 검토합니다.</p>

        <div
          v-if="missingRequired.length"
          class="alert"
          style="background: #fff4e5; color: var(--warn)"
        >
          미입력 필수 항목 {{ missingRequired.length }}개: {{ missingRequired.map((f) => f.label).join(', ') }}
        </div>

        <div class="card">
          <dl class="review">
            <template v-for="f in fields" :key="f.key">
              <dt>
                {{ f.label || f.key }}
                <span v-if="f.required" class="req">*</span>
              </dt>
              <dd :class="{ empty: !isFilled(f) }">
                <template v-if="!isFilled(f)">미입력</template>
                <template v-else-if="f.type === 'FILE'">첨부 완료</template>
                <template v-else>{{ data[f.key] }}</template>
              </dd>
            </template>
          </dl>
        </div>

        <div class="row">
          <button class="btn ghost" @click="step = 'fill'">← 수정</button>
          <button class="btn" @click="onSubmit">이대로 제출</button>
        </div>
      </template>

      <!-- 3. 완료 -->
      <template v-else>
        <div class="card stack">
          <div class="row" style="gap: 6px">
            <span class="badge" :class="result.complete ? 'b-ok' : 'b-warn'">
              {{ result.complete ? '작성 완료' : '미완 (누락 있음)' }}
            </span>
            <span class="small muted">버전 {{ result.version }}</span>
          </div>
          <div
            v-if="result.missingFields?.length"
            class="alert"
            style="background: #fff4e5; color: var(--warn)"
          >
            누락 필드: {{ result.missingFields.join(', ') }}
          </div>
          <div v-if="result.validationErrors?.length" class="alert err">
            확인 필요: {{ result.validationErrors.join(' / ') }}
          </div>
          <p class="small muted">
            제출되었습니다. 손해사정사가 “요청” 탭에서 내용을 확인합니다.
            {{ result.complete ? '' : ' 누락 항목은 요청받으면 다시 작성해 주세요.' }}
          </p>
        </div>
        <RouterLink :to="`/cases/${id}`" class="btn ghost">청구 상세로 돌아가기</RouterLink>
      </template>
    </template>
  </div>
</template>

<style scoped>
.steps span.on {
  color: var(--brand);
  font-weight: 700;
}
.bar {
  height: 6px;
  border-radius: 999px;
  background: var(--surface);
  overflow: hidden;
}
.bar-fill {
  height: 100%;
  background: var(--brand);
  transition: width 0.2s;
}
.ok-text {
  color: var(--ok);
  font-weight: 700;
}
.review {
  display: grid;
  grid-template-columns: 160px 1fr;
  gap: 10px 16px;
  margin: 0;
  font-size: 14px;
}
.review dt {
  color: var(--muted);
}
.review dd {
  margin: 0;
  white-space: pre-wrap;
}
.review dd.empty {
  color: var(--warn);
}
.review .req {
  color: var(--brand);
}
</style>
