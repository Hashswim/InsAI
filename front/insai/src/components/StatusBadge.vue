<script setup>
import { computed } from 'vue'

const props = defineProps({ status: { type: String, default: '' } })

const MAP = {
  // 케이스
  OPEN: ['접수', 'b-muted'],
  DOCS_REQUESTED: ['서류 요청됨', 'b-warn'],
  DOCS_SUBMITTED: ['서류 제출됨', 'b-info'],
  UNDER_REVIEW: ['심사 중', 'b-brand'],
  DECISION_MADE: ['결정 완료', 'b-ok'],
  CLOSED: ['종결', 'b-muted'],
  // 서류 요청
  PENDING: ['작성 대기', 'b-warn'],
  SUBMITTED: ['제출됨', 'b-info'],
  ACCEPTED: ['승인', 'b-ok'],
  REJECTED: ['반려', 'b-danger'],
  SUPPLEMENT_REQUESTED: ['보완 요청', 'b-warn'],
}

const label = computed(() => MAP[props.status]?.[0] || props.status)
const cls = computed(() => MAP[props.status]?.[1] || 'b-muted')
</script>

<template>
  <span class="badge" :class="cls">{{ label }}</span>
</template>
