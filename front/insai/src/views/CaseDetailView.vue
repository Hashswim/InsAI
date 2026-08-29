<script setup>
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import * as casesApi from '@/api/cases'
import * as formsApi from '@/api/forms'
import { claimTypes, requiredDocuments } from '@/api/catalog'
import StatusBadge from '@/components/StatusBadge.vue'
import CaseTimeline from '@/components/CaseTimeline.vue'

const props = defineProps({ id: { type: String, required: true } })
const auth = useAuthStore()

const caseData = ref(null)
const err = ref('')
const tab = ref('docs')

const docs = ref([])
const messages = ref([])
const timeline = ref([])

const myRole = computed(() => {
  if (!caseData.value) return null
  if (caseData.value.claimantId === auth.user?.id) return 'CLAIMANT'
  if (caseData.value.adjusterId === auth.user?.id) return 'ADJUSTER'
  return null
})
const canTakeCase = computed(
  () => !caseData.value?.adjusterId && auth.isAdjuster && caseData.value?.claimantId !== auth.user?.id,
)

async function loadCase() {
  err.value = ''
  try {
    caseData.value = await casesApi.getCase(props.id)
  } catch (e) {
    err.value = e.message
  }
}
async function loadDocs() {
  docs.value = await casesApi.listDocumentRequests(props.id)
}
async function loadMessages() {
  messages.value = await casesApi.listMessages(props.id)
}
async function loadTimeline() {
  timeline.value = await casesApi.getTimeline(props.id)
}

onMounted(async () => {
  await loadCase()
  await Promise.all([loadDocs(), loadTimeline()])
})

async function takeCase() {
  try {
    await casesApi.assignAdjuster(props.id, auth.user.id)
    await loadCase()
    await loadTimeline()
  } catch (e) {
    err.value = e.message
  }
}

/* ---- 서류 요청 추가 (사정사) ---- */
const adding = ref(false)
const types = ref([])
const selectedType = ref('')
const required = ref([])
const addErr = ref('')

async function openAdd() {
  adding.value = true
  addErr.value = ''
  if (!types.value.length) types.value = await claimTypes()
}
async function onPickType() {
  required.value = selectedType.value ? await requiredDocuments(selectedType.value) : []
}
function alreadyRequested(rd) {
  // DocumentRequestView 에는 templateCode 가 없어 제목(요청 시 서류명으로 설정)으로 근사 판별
  return docs.value.some((d) => d.title === rd.name)
}
async function addRequest(rd) {
  addErr.value = ''
  try {
    const tpl = await formsApi.getTemplateByCode(rd.templateCode)
    await casesApi.createDocumentRequest(props.id, { templateId: tpl.id, title: rd.name })
    await Promise.all([loadDocs(), loadCase(), loadTimeline()])
  } catch (e) {
    addErr.value = e.message
  }
}

/* ---- 제출물 검토 (사정사) ---- */
const openReview = ref(null) // documentRequestId
const submission = ref(null)
const reviewErr = ref('')

async function toggleReview(dr) {
  reviewErr.value = ''
  if (openReview.value === dr.id) {
    openReview.value = null
    return
  }
  openReview.value = dr.id
  submission.value = null
  try {
    submission.value = await formsApi.getSubmissionByDocRequest(dr.id)
  } catch (e) {
    reviewErr.value = e.message
  }
}
async function decide(dr, action) {
  let reason = null
  if (action === 'REJECT') {
    reason = window.prompt('반려 사유를 입력하세요')
    if (!reason) return
  }
  try {
    await casesApi.updateDocumentRequest(dr.id, action, reason)
    await loadDocs()
  } catch (e) {
    reviewErr.value = e.message
  }
}

/* ---- 메시지 ---- */
const msgText = ref('')
const msgErr = ref('')
async function sendMessage() {
  if (!msgText.value.trim() || !myRole.value) return
  msgErr.value = ''
  try {
    await casesApi.postMessage(props.id, myRole.value, msgText.value.trim())
    msgText.value = ''
    await Promise.all([loadMessages(), loadTimeline()])
  } catch (e) {
    msgErr.value = e.message
  }
}
function switchTab(t) {
  tab.value = t
  if (t === 'messages') loadMessages()
  if (t === 'timeline') loadTimeline()
}

/* ---- 사정 결과 (사정사) ---- */
const decisionText = ref('')
const decisionErr = ref('')
const decisionDone = ref(false)
async function beginReview() {
  decisionErr.value = ''
  try {
    await casesApi.startReview(props.id)
    await Promise.all([loadCase(), loadTimeline()])
  } catch (e) {
    decisionErr.value = e.message
  }
}
async function submitDecision() {
  if (!decisionText.value.trim()) return
  decisionErr.value = ''
  try {
    await casesApi.registerDecision(props.id, decisionText.value.trim())
    decisionDone.value = true
    await Promise.all([loadCase(), loadTimeline()])
  } catch (e) {
    decisionErr.value = e.message
  }
}

const roleLabel = { CLAIMANT: '청구인', ADJUSTER: '담당 손해사정사' }
function fmt(at) {
  return at ? new Date(at).toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }) : ''
}
</script>

<template>
  <div class="stack">
    <RouterLink to="/cases" class="small muted">← 목록으로</RouterLink>
    <div v-if="err" class="alert err">{{ err }}</div>

    <template v-if="caseData">
      <div class="card stack" style="gap: 10px">
        <div class="between">
          <StatusBadge :status="caseData.status" />
          <span v-if="myRole" class="badge b-muted">{{ roleLabel[myRole] }}</span>
        </div>
        <h1>{{ caseData.title }}</h1>
        <div class="row small muted">
          <span>개설 {{ fmt(caseData.createdAt) }}</span>
          <span>·</span>
          <span>사정사 {{ caseData.adjusterId ? '배정됨' : '미배정' }}</span>
        </div>
        <button v-if="canTakeCase" class="btn sm" @click="takeCase">이 청구 배정받기</button>
      </div>

      <div class="tabs">
        <button :class="{ on: tab === 'docs' }" @click="switchTab('docs')">서류</button>
        <button :class="{ on: tab === 'messages' }" @click="switchTab('messages')">메시지</button>
        <button :class="{ on: tab === 'timeline' }" @click="switchTab('timeline')">타임라인</button>
        <button :class="{ on: tab === 'result' }" @click="switchTab('result')">사정 결과</button>
      </div>

      <!-- 서류 -->
      <section v-if="tab === 'docs'" class="stack">
        <div v-if="myRole === 'ADJUSTER'" class="between">
          <h2>서류 요청</h2>
          <button class="btn sm ghost" @click="adding ? (adding = false) : openAdd()">
            {{ adding ? '닫기' : '+ 서류 요청' }}
          </button>
        </div>

        <div v-if="adding" class="card stack">
          <div class="field">
            <label>청구 유형</label>
            <select v-model="selectedType" class="select" @change="onPickType">
              <option value="">선택하세요</option>
              <option v-for="t in types" :key="t.code" :value="t.code">{{ t.label }}</option>
            </select>
          </div>
          <div v-if="addErr" class="alert err">{{ addErr }}</div>
          <ul v-if="required.length" class="req-list">
            <li v-for="rd in required" :key="rd.templateCode" class="between">
              <div>
                <div>
                  {{ rd.name }}
                  <span v-if="rd.mandatory" class="badge b-brand" style="margin-left: 6px">필수</span>
                  <span v-else class="badge b-muted" style="margin-left: 6px">조건부</span>
                </div>
                <div v-if="rd.condition" class="small muted">{{ rd.condition }}</div>
              </div>
              <button class="btn sm" :disabled="alreadyRequested(rd)" @click="addRequest(rd)">
                {{ alreadyRequested(rd) ? '요청됨' : '요청 추가' }}
              </button>
            </li>
          </ul>
        </div>

        <div v-if="!docs.length" class="card muted">아직 요청된 서류가 없습니다.</div>
        <div v-for="d in docs" :key="d.id" class="card stack" style="gap: 8px">
          <div class="between">
            <h3>{{ d.title }}</h3>
            <StatusBadge :status="d.status" />
          </div>
          <div v-if="d.dueDate" class="small muted">제출 기한 {{ d.dueDate }}</div>
          <div v-if="d.rejectionReason" class="alert err">반려 사유: {{ d.rejectionReason }}</div>

          <div class="row">
            <RouterLink
              v-if="myRole === 'CLAIMANT' && d.status !== 'ACCEPTED'"
              class="btn sm"
              :to="`/cases/${id}/requests/${d.id}/submit?templateId=${d.templateId}`"
            >
              {{ d.status === 'PENDING' ? '작성하기' : '수정 후 재제출' }}
            </RouterLink>

            <button
              v-if="myRole === 'ADJUSTER' && d.status !== 'PENDING'"
              class="btn sm ghost"
              @click="toggleReview(d)"
            >
              {{ openReview === d.id ? '접기' : '제출 내용 보기' }}
            </button>
          </div>

          <div v-if="myRole === 'ADJUSTER' && openReview === d.id" class="review">
            <div v-if="reviewErr" class="alert err">{{ reviewErr }}</div>
            <template v-if="submission">
              <div class="row" style="gap: 6px">
                <span class="badge" :class="submission.complete ? 'b-ok' : 'b-warn'">
                  {{ submission.complete ? '완료' : '미완' }}
                </span>
                <span class="small muted">버전 {{ submission.version }}</span>
              </div>
              <dl class="kv" style="margin-top: 10px">
                <template v-for="(v, k) in submission.data" :key="k">
                  <dt>{{ k }}</dt>
                  <dd>{{ v }}</dd>
                </template>
              </dl>
              <div v-if="submission.missingFields?.length" class="alert warn-box">
                누락 필드: {{ submission.missingFields.join(', ') }}
              </div>
              <div v-if="submission.validationErrors?.length" class="alert err">
                검증 오류: {{ submission.validationErrors.join(' / ') }}
              </div>
              <div class="row" style="margin-top: 10px">
                <button class="btn sm" @click="decide(d, 'ACCEPT')">승인</button>
                <button class="btn sm danger" @click="decide(d, 'REJECT')">반려</button>
              </div>
            </template>
          </div>
        </div>
      </section>

      <!-- 메시지 -->
      <section v-else-if="tab === 'messages'" class="stack">
        <h2>메시지</h2>
        <div v-if="!messages.length" class="card muted">주고받은 메시지가 없습니다.</div>
        <div
          v-for="m in messages"
          :key="m.id"
          class="msg"
          :class="{ mine: m.senderId === auth.user?.id }"
        >
          <div class="small muted">
            {{ roleLabel[m.senderRole] || m.senderRole }} · {{ fmt(m.createdAt) }}
          </div>
          <div class="bubble">{{ m.body }}</div>
        </div>

        <form v-if="myRole" class="card stack" @submit.prevent="sendMessage">
          <textarea v-model="msgText" class="textarea" placeholder="메시지를 입력하세요" />
          <div v-if="msgErr" class="alert err">{{ msgErr }}</div>
          <button class="btn sm" type="submit" :disabled="!msgText.trim()">보내기</button>
        </form>
      </section>

      <!-- 타임라인 -->
      <section v-else-if="tab === 'timeline'">
        <h2 style="margin-bottom: 14px">타임라인</h2>
        <CaseTimeline :events="timeline" />
      </section>

      <!-- 사정 결과 -->
      <section v-else class="stack">
        <h2>사정 결과</h2>

        <template v-if="myRole === 'ADJUSTER'">
          <div v-if="decisionErr" class="alert err">{{ decisionErr }}</div>

          <div v-if="caseData.status === 'DOCS_SUBMITTED'" class="card stack">
            <p class="muted small">제출된 서류를 확인했다면 심사를 시작하세요.</p>
            <button class="btn sm" @click="beginReview">심사 시작</button>
          </div>

          <div v-else-if="caseData.status === 'UNDER_REVIEW'" class="card stack">
            <div class="field">
              <label>사정 결과 (판단 내용)</label>
              <textarea
                v-model="decisionText"
                class="textarea"
                placeholder="예: 약관 제O조에 따라 사망보험금 전액 지급 결정. 총 OOO원."
              />
            </div>
            <button class="btn sm" :disabled="!decisionText.trim()" @click="submitDecision">
              결정 등록
            </button>
          </div>

          <div v-else-if="['DECISION_MADE', 'CLOSED'].includes(caseData.status)" class="card">
            <div class="alert ok">사정 결과가 등록되었습니다.</div>
            <p v-if="decisionDone && decisionText" class="small" style="margin-top: 10px; white-space: pre-wrap">
              {{ decisionText }}
            </p>
          </div>

          <div v-else class="card muted">서류 제출이 완료되면 심사를 진행할 수 있습니다.</div>
        </template>

        <template v-else>
          <div class="card muted">
            <template v-if="caseData.status === 'UNDER_REVIEW'">손해사정사가 서류를 심사하고 있습니다.</template>
            <template v-else-if="['DECISION_MADE', 'CLOSED'].includes(caseData.status)">
              사정 결과가 등록되었습니다. 자세한 내용은 담당 손해사정사에게 문의하세요.
            </template>
            <template v-else>아직 사정 결과가 없습니다.</template>
          </div>
        </template>
      </section>
    </template>
  </div>
</template>

<style scoped>
.req-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.review {
  border-top: 1px dashed var(--border);
  padding-top: 12px;
}
.warn-box {
  background: #fff4e5;
  color: var(--warn);
}
.msg {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
}
.msg.mine {
  align-items: flex-end;
}
.bubble {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 10px 14px;
  max-width: 78%;
  white-space: pre-wrap;
}
.msg.mine .bubble {
  background: var(--brand-tint);
  border-color: #ffd9d9;
}
</style>
