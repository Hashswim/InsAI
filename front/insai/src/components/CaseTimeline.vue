<script setup>
defineProps({ events: { type: Array, default: () => [] } })

const LABEL = {
  CASE_OPENED: '케이스 개설',
  ADJUSTER_ASSIGNED: '손해사정사 배정',
  DOCS_REQUESTED: '서류 요청',
  DOCS_SUBMITTED: '서류 제출',
  REVIEW_STARTED: '심사 시작',
  DECISION_MADE: '사정 결과 등록',
  CASE_CLOSED: '케이스 종결',
  MESSAGE_POSTED: '메시지',
}

function fmt(at) {
  if (!at) return ''
  const d = new Date(at)
  return d.toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })
}
</script>

<template>
  <ol class="tl">
    <li v-for="(e, i) in events" :key="i">
      <span class="dot" />
      <div>
        <div class="tl-title">{{ LABEL[e.type] || e.type }}</div>
        <div class="small muted">{{ fmt(e.at) }}</div>
      </div>
    </li>
    <li v-if="!events.length" class="muted small">아직 활동이 없습니다.</li>
  </ol>
</template>

<style scoped>
.tl {
  list-style: none;
  margin: 0;
  padding: 0;
  border-left: 2px solid var(--border);
}
.tl li {
  position: relative;
  padding: 4px 0 16px 20px;
}
.tl li:last-child {
  padding-bottom: 0;
}
.dot {
  position: absolute;
  left: -7px;
  top: 8px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--brand);
  border: 2px solid #fff;
}
.tl-title {
  font-weight: 600;
  font-size: 14px;
}
</style>
