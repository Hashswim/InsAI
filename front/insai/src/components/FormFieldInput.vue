<script setup>
import { ref } from 'vue'
import { uploadFile } from '@/api/files'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [String, Number], default: '' },
  caseId: { type: String, default: '' },
  submissionId: { type: String, default: null },
})
const emit = defineEmits(['update:modelValue'])

function on(e) {
  emit('update:modelValue', e.target.value)
}

const uploading = ref(false)
const uploadErr = ref('')
const uploadedName = ref('')

async function onFile(e) {
  const file = e.target.files?.[0]
  if (!file) return
  uploadErr.value = ''
  uploading.value = true
  try {
    const fileId = await uploadFile({
      caseId: props.caseId,
      submissionId: props.submissionId,
      file,
    })
    uploadedName.value = file.name
    emit('update:modelValue', fileId)
  } catch (err) {
    uploadErr.value = err.message || '업로드에 실패했습니다.'
    e.target.value = ''
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div class="field">
    <label>
      {{ field.label || field.key }}
      <span v-if="field.required" class="req">*</span>
    </label>

    <select
      v-if="field.type === 'SELECT'"
      class="select"
      :value="modelValue"
      @change="on"
    >
      <option value="">선택하세요</option>
      <option v-for="opt in field.options || []" :key="opt" :value="opt">{{ opt }}</option>
    </select>

    <input
      v-else-if="field.type === 'DATE'"
      class="input"
      type="date"
      :value="modelValue"
      @input="on"
    />

    <input
      v-else-if="field.type === 'NUMBER'"
      class="input"
      type="number"
      :min="field.min ?? undefined"
      :max="field.max ?? undefined"
      :value="modelValue"
      @input="on"
    />

    <div v-else-if="field.type === 'FILE'" class="stack" style="gap: 6px">
      <div v-if="modelValue" class="alert ok" style="display: flex; gap: 10px; align-items: center">
        <span>첨부됨: {{ uploadedName || '기존 파일' }}</span>
        <label class="btn ghost small" style="cursor: pointer">
          다시 선택
          <input type="file" accept=".pdf,application/pdf" hidden :disabled="uploading" @change="onFile" />
        </label>
      </div>
      <label v-else class="btn ghost" style="cursor: pointer; align-self: flex-start">
        {{ uploading ? '업로드 중…' : '파일 선택' }}
        <input type="file" accept=".pdf,application/pdf" hidden :disabled="uploading" @change="onFile" />
      </label>
      <p class="small muted">PDF만, 10MB 이하.</p>
      <div v-if="uploadErr" class="alert err">{{ uploadErr }}</div>
    </div>

    <textarea
      v-else-if="field.type === 'TEXT' && (field.maxLength ?? 0) > 200"
      class="input"
      rows="4"
      :maxlength="field.maxLength"
      placeholder="손해사정사에게 묻고 싶은 점이 있으면 적어 주세요 (선택)"
      :value="modelValue"
      @input="on"
    />

    <input
      v-else
      class="input"
      type="text"
      :maxlength="field.maxLength ?? undefined"
      :value="modelValue"
      @input="on"
    />
  </div>
</template>
