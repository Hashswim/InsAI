<script setup>
import { ref } from 'vue'

const props = defineProps({
  accent: { type: String, default: 'brand' }, // brand | indigo | ok | muted
  title: { type: String, required: true },
  date: { type: String, default: '' },
  isNew: { type: Boolean, default: false },
  attachment: { type: Boolean, default: false },
  defaultOpen: { type: Boolean, default: false },
})
const emit = defineEmits(['open'])

const open = ref(props.defaultOpen)
function toggle() {
  open.value = !open.value
  if (open.value) emit('open')
}

const ACCENT = {
  brand: '#ff5252',
  indigo: '#6366f1',
  ok: '#12b76a',
  muted: '#cbd2dc',
}
</script>

<template>
  <div class="fc" :style="{ '--accent': ACCENT[accent] || ACCENT.brand }">
    <button class="fc-head" @click="toggle">
      <div class="fc-title-row">
        <span class="fc-title">{{ title }}</span>
        <span v-if="isNew" class="badge b-brand">NEW</span>
      </div>
      <div class="fc-meta small muted">
        <span v-if="date">{{ date }}</span>
        <span v-if="attachment">· 첨부</span>
      </div>
      <span class="fc-chev" :class="{ up: open }">▾</span>
    </button>
    <div v-if="open" class="fc-body">
      <slot />
      <div v-if="$slots.actions" class="fc-actions">
        <slot name="actions" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.fc {
  background: #fff;
  border: 1px solid var(--border);
  border-left: 4px solid var(--accent);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  overflow: hidden;
}
.fc-head {
  width: 100%;
  border: none;
  background: none;
  text-align: left;
  padding: 16px 18px;
  cursor: pointer;
  display: grid;
  grid-template-columns: 1fr auto;
  grid-template-areas:
    'title chev'
    'meta chev';
  align-items: center;
  gap: 4px 10px;
}
.fc-title-row {
  grid-area: title;
  display: flex;
  align-items: center;
  gap: 8px;
}
.fc-title {
  font-weight: 700;
  font-size: 15px;
}
.fc-meta {
  grid-area: meta;
  display: flex;
  gap: 6px;
}
.fc-chev {
  grid-area: chev;
  color: var(--muted);
  transition: transform 0.15s;
}
.fc-chev.up {
  transform: rotate(180deg);
}
.fc-body {
  padding: 0 18px 18px;
  border-top: 1px solid var(--border);
  padding-top: 14px;
  white-space: pre-wrap;
  font-size: 14px;
  line-height: 1.6;
}
.fc-actions {
  display: flex;
  gap: 8px;
  margin-top: 14px;
  flex-wrap: wrap;
}
</style>
