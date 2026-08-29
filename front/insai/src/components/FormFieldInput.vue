<script setup>
const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [String, Number], default: '' },
})
const emit = defineEmits(['update:modelValue'])

function on(e) {
  emit('update:modelValue', e.target.value)
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

    <div v-else-if="field.type === 'FILE'" class="alert info">
      파일 첨부는 준비 중입니다. 파일명 등 텍스트 정보만 입력해 주세요.
    </div>

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
