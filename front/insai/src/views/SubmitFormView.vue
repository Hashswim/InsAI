<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as formsApi from '@/api/forms'
import FormFieldInput from '@/components/FormFieldInput.vue'

const props = defineProps({
  id: { type: String, required: true }, // caseId
  docRequestId: { type: String, required: true },
})
const route = useRoute()
const router = useRouter()

const templateId = route.query.templateId
const template = ref(null)
const data = reactive({})
const err = ref('')
const loading = ref(true)
const result = ref(null)
const saved = ref(false)

onMounted(async () => {
  try {
    template.value = await formsApi.getTemplate(templateId)
    for (const f of template.value.fields) data[f.key] = ''
    // 이전 제출/임시저장이 있으면 채워넣기
    try {
      const prev = await formsApi.getSubmissionByDocRequest(props.docRequestId)
      if (prev?.data) for (const [k, v] of Object.entries(prev.data)) if (k in data) data[k] = v
    } catch {
      /* 없으면 무시 */
    }
  } catch (e) {
    err.value = e.message
  } finally {
    loading.value = false
  }
})

function payload() {
  const clean = {}
  for (const [k, v] of Object.entries(data)) if (v !== '' && v != null) clean[k] = v
  return {
    documentRequestId: props.docRequestId,
    caseId: props.id,
    templateId,
    data: clean,
  }
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

async function onSubmit() {
  err.value = ''
  try {
    await formsApi.submitForm(payload())
    result.value = await formsApi.getSubmissionByDocRequest(props.docRequestId)
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

    <template v-if="template && !result">
      <h1>{{ template.name }}</h1>
      <p class="muted small">필요한 칸을 채워 제출하세요. 필수 항목이 비어 있어도 제출은 되며, 누락으로 표시됩니다.</p>

      <form class="card stack" @submit.prevent="onSubmit">
        <FormFieldInput
          v-for="f in template.fields"
          :key="f.key"
          :field="f"
          v-model="data[f.key]"
        />
        <div v-if="saved" class="alert ok">임시저장했습니다.</div>
        <div class="row">
          <button type="button" class="btn ghost" @click="onSaveDraft">임시저장</button>
          <button type="submit" class="btn">제출</button>
        </div>
      </form>
    </template>

    <template v-if="result">
      <h1>제출 완료</h1>
      <div class="card stack">
        <div class="row" style="gap: 6px">
          <span class="badge" :class="result.complete ? 'b-ok' : 'b-warn'">
            {{ result.complete ? '완료' : '미완 (누락 있음)' }}
          </span>
          <span class="small muted">버전 {{ result.version }}</span>
        </div>
        <div v-if="result.missingFields?.length" class="alert" style="background: #fff4e5; color: var(--warn)">
          누락 필드: {{ result.missingFields.join(', ') }}
        </div>
        <div v-if="result.validationErrors?.length" class="alert err">
          검증 오류: {{ result.validationErrors.join(' / ') }}
        </div>
        <p v-if="result.complete && !result.validationErrors?.length" class="small muted">
          모든 필수 항목이 채워졌습니다. 손해사정사가 검토합니다.
        </p>
      </div>
      <RouterLink :to="`/cases/${id}`" class="btn ghost">청구 상세로 돌아가기</RouterLink>
    </template>
  </div>
</template>
