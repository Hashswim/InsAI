<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { openCase } from '@/api/cases'
import { CLAIM_TYPES } from '@/lib/claimType'

const router = useRouter()
const title = ref('')
const claimType = ref('')
const err = ref('')
const loading = ref(false)

async function onSubmit() {
  err.value = ''
  loading.value = true
  try {
    const { caseId } = await openCase(title.value.trim(), claimType.value || null)
    router.push(`/cases/${caseId}`)
  } catch (e) {
    err.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="stack" style="max-width: 560px">
    <RouterLink to="/cases" class="small muted">← 목록으로</RouterLink>
    <h1>새 청구 시작</h1>
    <p class="muted small">
      청구 유형과 개요를 적어주세요. 개설 후 손해사정사가 배정되고, 필요한 서류를 요청하면 이곳에서
      작성·제출합니다.
    </p>

    <form class="card stack" @submit.prevent="onSubmit">
      <div class="field">
        <label>청구 유형 <span class="req">*</span></label>
        <div class="row" style="flex-wrap: wrap">
          <button
            v-for="t in CLAIM_TYPES"
            :key="t.code"
            type="button"
            class="chip"
            :class="{ on: claimType === t.code }"
            @click="claimType = t.code"
          >
            {{ t.label }}
          </button>
        </div>
      </div>

      <div class="field">
        <label>청구 개요 <span class="req">*</span></label>
        <input
          v-model="title"
          class="input"
          placeholder="예: 부친 사망보험금 청구 / 교통사고 후유장해 청구"
          required
        />
      </div>

      <div v-if="err" class="alert err">{{ err }}</div>
      <button class="btn" type="submit" :disabled="loading || !title.trim() || !claimType">
        {{ loading ? '개설 중…' : '청구 개설' }}
      </button>
    </form>
  </div>
</template>
