<script setup>
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import * as casesApi from '@/api/cases'
import * as formsApi from '@/api/forms'
import { openFile } from '@/api/files'
import { claimTypes, requiredDocuments } from '@/api/catalog'
import { claimTypeLabel } from '@/lib/claimType'
import { isRead, markRead } from '@/lib/readState'
import StatusBadge from '@/components/StatusBadge.vue'
import CaseTimeline from '@/components/CaseTimeline.vue'
import FeedCard from '@/components/FeedCard.vue'

const props = defineProps({ id: { type: String, required: true } })
const auth = useAuthStore()

const caseData = ref(null)
const err = ref('')
const readTick = ref(0)

const docs = ref([])
const messages = ref([])
const timeline = ref([])

const myRole = computed(() => {
  if (!caseData.value) return null
  if (caseData.value.claimantId === auth.user?.id) return 'CLAIMANT'
  if (caseData.value.adjusterId === auth.user?.id) return 'ADJUSTER'
  return null
})
const isAdj = computed(() => myRole.value === 'ADJUSTER')
const canTakeCase = computed(
  () => !caseData.value?.adjusterId && auth.isAdjuster && caseData.value?.claimantId !== auth.user?.id,
)
const caseNo = computed(() =>
  caseData.value ? caseData.value.id.replace(/-/g, '').slice(0, 10).toUpperCase() : '',
)

const nav = ref('todo')
const NAV_CLAIMANT = [
  { k: 'todo', label: '해야 할 일', icon: '✅' },
  { k: 'inquiry', label: '문의', icon: '💬' },
  { k: 'notice', label: '안내 사항', icon: '🔔' },
  { k: 'progress', label: '진행 상황', icon: '🗂' },
  { k: 'result', label: '사정 결과', icon: '⚖️' },
  { k: 'info', label: '청구 정보', icon: 'ℹ️' },
]
const NAV_ADJUSTER = [
  { k: 'overview', label: '개요', icon: '📊' },
  { k: 'requests', label: '서류 검토', icon: '📄' },
  { k: 'result', label: '심사·결정', icon: '⚖️' },
  { k: 'inquiry', label: '청구인과 소통', icon: '💬' },
  { k: 'info', label: '청구 정보', icon: 'ℹ️' },
]
const navItems = computed(() => (isAdj.value ? NAV_ADJUSTER : NAV_CLAIMANT))

function fmtDate(at) {
  return at ? new Date(at).toLocaleDateString('ko-KR') : ''
}
function fmtDateTime(at) {
  return at ? new Date(at).toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }) : ''
}
function readKey(k) {
  readTick.value
  return isRead(props.id, k)
}
function acknowledge(k) {
  markRead(props.id, k)
  readTick.value++
}

async function loadCase() {
  err.value = ''
  try {
    caseData.value = await casesApi.getCase(props.id)
  } catch (e) {
    err.value = e.message
  }
}
const loadDocs = async () => (docs.value = await casesApi.listDocumentRequests(props.id))
const loadMessages = async () => (messages.value = await casesApi.listMessages(props.id))
const loadTimeline = async () => (timeline.value = await casesApi.getTimeline(props.id))

onMounted(async () => {
  await loadCase()
  nav.value = isAdj.value ? 'overview' : 'todo'
  await Promise.all([loadDocs(), loadMessages(), loadTimeline()])
})

async function takeCase() {
  try {
    await casesApi.assignAdjuster(props.id, auth.user.id)
    await Promise.all([loadCase(), loadTimeline()])
    nav.value = 'requests'
  } catch (e) {
    err.value = e.message
  }
}

/* ---------- 해야 할 일 (청구인) ---------- */
const todoDocs = computed(() =>
  [...docs.value]
    .filter((d) => ['PENDING', 'REJECTED'].includes(d.status))
    .sort((a, b) => new Date(a.createdAt || 0) - new Date(b.createdAt || 0)),
)
const doneDocs = computed(() =>
  [...docs.value].filter((d) => ['SUBMITTED', 'ACCEPTED'].includes(d.status)),
)

/* ---------- 안내 사항 (청구인) ---------- */
const noticeFeed = computed(() => {
  const items = []
  const T = {
    ADJUSTER_ASSIGNED: ['손해사정사 배정', '담당 손해사정사가 배정되었습니다. 이제 서류 요청이 전달됩니다.'],
    DOCS_REQUESTED: ['서류 요청', '제출이 필요한 서류가 있습니다. “해야 할 일”에서 확인해 작성해 주세요.'],
    REVIEW_STARTED: ['심사 시작', '제출하신 서류에 대한 심사가 시작되었습니다.'],
    DECISION_MADE: ['사정 결과 등록', '사정 결과가 등록되었습니다. 자세한 내용은 문의로 확인해 주세요.'],
    CASE_CLOSED: ['청구 종결', '청구 건이 종결되었습니다.'],
  }
  timeline.value.forEach((e) => {
    if (T[e.type]) {
      items.push({ key: `tl-${e.type}-${e.at}`, title: T[e.type][0], body: T[e.type][1], at: e.at })
    }
  })
  docs.value.forEach((d) => {
    if (d.status === 'ACCEPTED')
      items.push({ key: `dr-${d.id}-A`, title: '서류 승인', body: `‘${d.title}’ 서류가 승인되었습니다.`, at: null })
    if (d.status === 'REJECTED')
      items.push({
        key: `dr-${d.id}-R`,
        title: '서류 반려',
        body: `‘${d.title}’ 서류가 반려되었습니다.${d.rejectionReason ? ' 사유: ' + d.rejectionReason : ''} “해야 할 일”에서 다시 작성해 주세요.`,
        at: null,
      })
  })
  return items.sort((a, b) => new Date(b.at || Date.now()) - new Date(a.at || Date.now()))
})
const unreadNotices = computed(() => noticeFeed.value.filter((i) => !readKey(i.key)).length)

/* ---------- 문의 (양방향) ---------- */
const inquiryText = ref('')
const inquiryErr = ref('')
async function sendInquiry() {
  if (!inquiryText.value.trim() || !myRole.value) return
  inquiryErr.value = ''
  try {
    await casesApi.postMessage(props.id, myRole.value, inquiryText.value.trim())
    inquiryText.value = ''
    await Promise.all([loadMessages(), loadTimeline()])
  } catch (e) {
    inquiryErr.value = e.message
  }
}

/* ---------- 개요 (사정사) ---------- */
const pendingReviewDocs = computed(() => docs.value.filter((d) => d.status === 'SUBMITTED'))
const acceptedDocs = computed(() => docs.value.filter((d) => d.status === 'ACCEPTED'))
const rejectedDocs = computed(() => docs.value.filter((d) => d.status === 'REJECTED'))
const daysOpen = computed(() => {
  if (!caseData.value?.createdAt) return null
  const ms = Date.now() - new Date(caseData.value.createdAt).getTime()
  return Math.max(0, Math.floor(ms / 86400000))
})
const actionNeeded = computed(() => {
  if (!caseData.value) return null
  if (pendingReviewDocs.value.length) {
    return {
      text: `제출된 서류 ${pendingReviewDocs.value.length}건이 검토를 기다리고 있습니다.`,
      cta: '서류 검토하기',
      go: 'requests',
    }
  }
  switch (caseData.value.status) {
    case 'DOCS_REQUESTED':
      return { text: '청구인의 서류 제출을 기다리는 중입니다.', cta: null }
    case 'DOCS_SUBMITTED':
      return { text: '서류 제출이 완료되었습니다. 심사를 시작할 수 있습니다.', cta: '심사·결정으로 가기', go: 'result' }
    case 'UNDER_REVIEW':
      return { text: '심사가 진행 중입니다. 결정을 등록하세요.', cta: '심사·결정으로 가기', go: 'result' }
    case 'DECISION_MADE':
      return { text: '결정이 등록되었습니다. 청구 건을 종결할 수 있습니다.', cta: '심사·결정으로 가기', go: 'result' }
    case 'CLOSED':
      return { text: '종결된 청구 건입니다.', cta: null }
    default:
      return { text: '지금 처리할 항목이 없습니다.', cta: null }
  }
})
const recentActivity = computed(() => [...timeline.value].slice(-4).reverse())

/* ---------- 서류 요청·검토 (사정사) ---------- */
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
const alreadyRequested = (rd) => docs.value.some((d) => d.title === rd.name)
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
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const isFileValue = (v) => typeof v === 'string' && UUID_RE.test(v)

const submissions = ref({})
async function loadSubmission(drId) {
  try {
    submissions.value = {
      ...submissions.value,
      [drId]: await formsApi.getSubmissionByDocRequest(drId),
    }
  } catch {
    submissions.value = { ...submissions.value, [drId]: 'none' }
  }
}
async function decide(drId, action) {
  let reason = null
  if (action === 'REJECT') {
    reason = window.prompt('반려 사유를 입력하세요')
    if (!reason) return
  }
  try {
    await casesApi.updateDocumentRequest(drId, action, reason)
    await Promise.all([loadDocs(), loadTimeline()])
  } catch (e) {
    err.value = e.message
  }
}

/* ---------- 사정 결과 ---------- */
const decisionText = ref('')
const decisionErr = ref('')
const decision = ref(null) // 조회된 사정 결과
const decisionMade = computed(() =>
  caseData.value && ['DECISION_MADE', 'CLOSED'].includes(caseData.value.status),
)
async function loadDecision() {
  if (!decisionMade.value) {
    decision.value = null
    return
  }
  try {
    decision.value = await casesApi.getDecision(props.id)
  } catch {
    decision.value = null
  }
}
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
    await Promise.all([loadCase(), loadTimeline()])
    await loadDecision()
  } catch (e) {
    decisionErr.value = e.message
  }
}
async function closeCase() {
  decisionErr.value = ''
  try {
    await casesApi.closeCase(props.id)
    await Promise.all([loadCase(), loadTimeline()])
  } catch (e) {
    decisionErr.value = e.message
  }
}

function goNav(k) {
  nav.value = k
  if (k === 'inquiry') loadMessages()
  if (k === 'progress') loadTimeline()
  if (k === 'result') loadDecision()
}
const roleLabel = { CLAIMANT: '청구인', ADJUSTER: '담당 손해사정사' }
const statusLabel = {
  OPEN: '접수',
  DOCS_REQUESTED: '서류 요청됨',
  DOCS_SUBMITTED: '서류 제출됨',
  UNDER_REVIEW: '심사 중',
  DECISION_MADE: '결정 완료',
  CLOSED: '종결',
}
</script>

<template>
  <div class="stack">
    <RouterLink to="/cases" class="small muted">← 목록으로</RouterLink>
    <div v-if="err" class="alert err">{{ err }}</div>

    <template v-if="caseData">
      <div class="detail">
        <!-- 사이드바 -->
        <aside class="side">
          <div class="side-head">
            <div class="small muted">사건 번호</div>
            <div class="side-no">{{ caseNo }}</div>
            <div class="row" style="gap: 6px; margin-top: 8px">
              <span class="chip on" style="cursor: default; height: 24px; font-size: 12px">
                {{ claimTypeLabel(caseData.claimType) }}
              </span>
              <StatusBadge :status="caseData.status" />
            </div>
          </div>
          <nav>
            <button
              v-for="n in navItems"
              :key="n.k"
              class="side-item"
              :class="{ on: nav === n.k }"
              @click="goNav(n.k)"
            >
              <span>{{ n.icon }}</span> {{ n.label }}
              <span v-if="n.k === 'todo' && todoDocs.length" class="pill">{{ todoDocs.length }}</span>
              <span v-if="n.k === 'requests' && pendingReviewDocs.length" class="pill">{{ pendingReviewDocs.length }}</span>
              <span v-if="n.k === 'notice' && unreadNotices" class="dot" />
            </button>
          </nav>
          <button v-if="canTakeCase" class="btn sm block" style="margin-top: 10px" @click="takeCase">
            이 청구 배정받기
          </button>
        </aside>

        <!-- 본문 -->
        <div class="main stack">
          <div class="between">
            <h1>{{ caseData.title }}</h1>
            <span v-if="myRole" class="badge b-muted">{{ roleLabel[myRole] }}</span>
          </div>

          <!-- 개요 (사정사) -->
          <section v-if="nav === 'overview'" class="stack">
            <div class="ov-summary card">
              <dl class="kv">
                <dt>청구인</dt>
                <dd>{{ caseData.claimantName || '—' }}</dd>
                <dt>청구 유형</dt>
                <dd>{{ claimTypeLabel(caseData.claimType) }}</dd>
                <dt>경과일</dt>
                <dd>{{ daysOpen != null ? `개설 ${daysOpen}일째` : '—' }}</dd>
              </dl>
            </div>

            <div class="ov-action card" :class="{ warn: actionNeeded?.cta }">
              <p>{{ actionNeeded?.text }}</p>
              <button v-if="actionNeeded?.cta" class="btn sm" @click="goNav(actionNeeded.go)">
                {{ actionNeeded.cta }}
              </button>
            </div>

            <div class="ov-stats">
              <div class="ov-stat">
                <b>{{ pendingReviewDocs.length }}</b><span>검토 대기</span>
              </div>
              <div class="ov-stat">
                <b>{{ acceptedDocs.length }}</b><span>승인</span>
              </div>
              <div class="ov-stat">
                <b>{{ rejectedDocs.length }}</b><span>반려</span>
              </div>
              <div class="ov-stat">
                <b>{{ messages.length }}</b><span>주고받은 메시지</span>
              </div>
            </div>

            <h2>최근 활동</h2>
            <CaseTimeline :events="recentActivity" />
          </section>

          <!-- 해야 할 일 -->
          <section v-else-if="nav === 'todo'" class="stack">
            <h2>해야 할 일 <span class="muted small">({{ todoDocs.length }})</span></h2>
            <p v-if="!todoDocs.length" class="card muted">지금 작성하실 서류가 없습니다.</p>

            <FeedCard
              v-for="d in todoDocs"
              :key="`todo-${d.id}`"
              accent="brand"
              :title="d.title"
              :date="fmtDate(d.createdAt)"
              :is-new="d.status === 'PENDING' && !readKey(`req-${d.id}`)"
              attachment
              default-open
              @open="acknowledge(`req-${d.id}`)"
            >
              <div class="row" style="gap: 6px; margin-bottom: 8px">
                <StatusBadge :status="d.status" />
                <span v-if="d.dueDate" class="small muted">제출 기한 {{ d.dueDate }}</span>
              </div>
              <div v-if="d.rejectionReason" class="alert err" style="margin-bottom: 8px">
                반려 사유: {{ d.rejectionReason }}
              </div>
              <p class="muted">아래 버튼에서 서류의 칸을 채워 제출하세요.</p>
              <template #actions>
                <RouterLink
                  class="btn sm"
                  :to="`/cases/${id}/requests/${d.id}/submit?templateId=${d.templateId}`"
                >
                  {{ d.status === 'REJECTED' ? '다시 작성하기' : '작성하기' }}
                </RouterLink>
              </template>
            </FeedCard>

            <template v-if="doneDocs.length">
              <div class="divider" />
              <h3 class="muted">제출한 서류</h3>
              <div v-for="d in doneDocs" :key="`done-${d.id}`" class="row between card" style="padding: 12px 16px">
                <span>{{ d.title }}</span>
                <StatusBadge :status="d.status" />
              </div>
            </template>
          </section>

          <!-- 문의 / 소통 -->
          <section v-else-if="nav === 'inquiry'" class="stack">
            <h2>{{ isAdj ? '청구인과 소통' : '문의' }}</h2>
            <p v-if="!messages.length" class="card muted">
              {{ isAdj ? '아직 주고받은 메시지가 없습니다.' : '주고받은 문의가 없습니다.' }}
            </p>
            <div
              v-for="m in messages"
              :key="m.id"
              class="msg"
              :class="{ mine: m.senderId === auth.user?.id }"
            >
              <div class="small muted">
                {{ roleLabel[m.senderRole] || m.senderRole }} · {{ fmtDateTime(m.createdAt) }}
              </div>
              <div class="bubble">{{ m.body }}</div>
            </div>
            <form v-if="myRole" class="card stack" @submit.prevent="sendInquiry">
              <textarea
                v-model="inquiryText"
                class="textarea"
                :placeholder="isAdj ? '청구인에게 진행 상황이나 안내를 남기세요' : '손해사정사에게 문의하세요'"
              />
              <div v-if="inquiryErr" class="alert err">{{ inquiryErr }}</div>
              <button class="btn sm" type="submit" :disabled="!inquiryText.trim()">보내기</button>
            </form>
          </section>

          <!-- 안내 사항 -->
          <section v-else-if="nav === 'notice'" class="stack">
            <h2>안내 사항 <span class="muted small">({{ noticeFeed.length }})</span></h2>
            <p v-if="!noticeFeed.length" class="card muted">아직 안내가 없습니다.</p>
            <FeedCard
              v-for="i in noticeFeed"
              :key="i.key"
              accent="indigo"
              :title="i.title"
              :date="fmtDateTime(i.at)"
              :is-new="!readKey(i.key)"
            >
              {{ i.body }}
              <template v-if="!readKey(i.key)" #actions>
                <button class="btn sm ghost" @click="acknowledge(i.key)">확인 완료</button>
              </template>
            </FeedCard>
          </section>

          <!-- 진행 상황 -->
          <section v-else-if="nav === 'progress'">
            <h2 style="margin-bottom: 14px">진행 상황</h2>
            <CaseTimeline :events="timeline" />
          </section>

          <!-- 청구 정보 -->
          <section v-else-if="nav === 'info'" class="stack">
            <h2>청구 정보</h2>
            <div class="card">
              <dl class="kv">
                <dt>사건 번호</dt>
                <dd>{{ caseNo }}</dd>
                <dt>청구 유형</dt>
                <dd>{{ claimTypeLabel(caseData.claimType) }}</dd>
                <dt>청구 개요</dt>
                <dd>{{ caseData.title }}</dd>
                <dt>현재 상태</dt>
                <dd>{{ statusLabel[caseData.status] || caseData.status }}</dd>
                <dt>청구인</dt>
                <dd>{{ caseData.claimantName || '—' }}</dd>
                <dt>담당 손해사정사</dt>
                <dd>{{ caseData.adjusterName || '미배정' }}</dd>
                <dt>개설일</dt>
                <dd>{{ fmtDate(caseData.createdAt) }}</dd>
              </dl>
            </div>
          </section>

          <!-- 서류 요청·검토 (사정사) -->
          <section v-else-if="nav === 'requests'" class="stack">
            <div class="between">
              <h2>서류 요청·검토 <span class="muted small">({{ docs.length }})</span></h2>
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
              <ul v-if="required.length" class="plain-list">
                <li v-for="rd in required" :key="rd.templateCode" class="between">
                  <div>
                    {{ rd.name }}
                    <span class="badge" :class="rd.mandatory ? 'b-brand' : 'b-muted'" style="margin-left: 6px">
                      {{ rd.mandatory ? '필수' : '조건부' }}
                    </span>
                    <div v-if="rd.condition" class="small muted">{{ rd.condition }}</div>
                  </div>
                  <button class="btn sm" :disabled="alreadyRequested(rd)" @click="addRequest(rd)">
                    {{ alreadyRequested(rd) ? '요청됨' : '요청 추가' }}
                  </button>
                </li>
              </ul>
            </div>

            <p v-if="!docs.length" class="card muted">아직 요청한 서류가 없습니다.</p>
            <FeedCard
              v-for="d in docs"
              :key="d.id"
              accent="brand"
              :title="d.title"
              :date="fmtDate(d.createdAt)"
              attachment
            >
              <div class="row" style="gap: 6px; margin-bottom: 8px">
                <StatusBadge :status="d.status" />
              </div>
              <div v-if="d.rejectionReason" class="alert err" style="margin-bottom: 8px">
                반려 사유: {{ d.rejectionReason }}
              </div>
              <template #actions>
                <button v-if="d.status !== 'PENDING'" class="btn sm ghost" @click="loadSubmission(d.id)">
                  제출 내용 불러오기
                </button>
              </template>

              <div v-if="submissions[d.id] && submissions[d.id] !== 'none'" class="review">
                <div class="row" style="gap: 6px">
                  <span class="badge" :class="submissions[d.id].complete ? 'b-ok' : 'b-warn'">
                    {{ submissions[d.id].complete ? '작성 완료' : '미완' }}
                  </span>
                  <span class="small muted">버전 {{ submissions[d.id].version }}</span>
                </div>
                <dl class="kv" style="margin-top: 10px">
                  <template v-for="(v, k) in submissions[d.id].data" :key="k">
                    <dt>{{ k }}</dt>
                    <dd>
                      <button
                        v-if="isFileValue(v)"
                        class="btn sm ghost"
                        @click="openFile(v)"
                      >
                        첨부 파일 열기
                      </button>
                      <template v-else>{{ v }}</template>
                    </dd>
                  </template>
                </dl>
                <div
                  v-if="submissions[d.id].missingFields?.length"
                  class="alert"
                  style="background: #fff4e5; color: var(--warn); margin-top: 8px"
                >
                  누락: {{ submissions[d.id].missingFields.join(', ') }}
                </div>
                <div v-if="d.status === 'SUBMITTED'" class="row" style="margin-top: 10px">
                  <button class="btn sm" @click="decide(d.id, 'ACCEPT')">승인</button>
                  <button class="btn sm danger" @click="decide(d.id, 'REJECT')">반려</button>
                </div>
              </div>
              <p v-else-if="submissions[d.id] === 'none'" class="small muted" style="margin-top: 8px">
                아직 제출된 내용이 없습니다.
              </p>
            </FeedCard>
          </section>

          <!-- 사정 결과 -->
          <section v-else-if="nav === 'result'" class="stack">
            <h2>사정 결과</h2>
            <div v-if="decisionErr" class="alert err">{{ decisionErr }}</div>

            <!-- 등록된 결과 (양측 공통) -->
            <div v-if="decision" class="card stack">
              <div class="row" style="gap: 6px">
                <span class="badge b-ok">사정 결과</span>
                <span class="small muted">
                  {{ decision.adjusterName || '손해사정사' }} · {{ fmtDateTime(decision.decidedAt) }}
                </span>
              </div>
              <p v-if="decision.plainText" style="white-space: pre-wrap">{{ decision.plainText }}</p>
              <p style="white-space: pre-wrap" :class="{ muted: !!decision.plainText, small: !!decision.plainText }">
                {{ decision.rawText }}
              </p>
            </div>

            <!-- 사정사 컨트롤 -->
            <template v-if="isAdj">
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
              <div v-else-if="caseData.status === 'DECISION_MADE'" class="card stack">
                <p class="muted small">결과 전달이 완료되었으면 청구 건을 종결하세요.</p>
                <button class="btn sm" @click="closeCase">청구 종결</button>
              </div>
              <div v-else-if="caseData.status === 'CLOSED'" class="card muted">종결된 청구 건입니다.</div>
              <div v-else class="card muted">서류 제출이 완료되면 심사를 진행할 수 있습니다.</div>
            </template>

            <!-- 청구인 안내 -->
            <template v-else-if="!decision">
              <div class="card muted">
                <template v-if="caseData.status === 'UNDER_REVIEW'">손해사정사가 서류를 심사하고 있습니다.</template>
                <template v-else>아직 사정 결과가 없습니다.</template>
              </div>
            </template>
          </section>

          <section v-else />

          <p v-if="!isAdj" class="guide small">
            온라인으로 진행되므로 “안내 사항”을 확인하고 “해야 할 일”의 서류를 정확히 작성·제출해 주세요.
          </p>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.detail {
  display: grid;
  grid-template-columns: 210px 1fr;
  gap: 20px;
  align-items: start;
}
@media (max-width: 780px) {
  .detail {
    grid-template-columns: 1fr;
  }
  .side {
    position: static !important;
  }
  .side nav {
    flex-direction: row !important;
    overflow-x: auto;
  }
}
.side {
  position: sticky;
  top: 76px;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 14px;
}
.side-head {
  padding: 4px 6px 12px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 8px;
}
.side-no {
  font-weight: 800;
  font-size: 15px;
  letter-spacing: 0.02em;
}
.side nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.side-item {
  display: flex;
  align-items: center;
  gap: 8px;
  border: none;
  background: none;
  padding: 10px 8px;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-2);
  cursor: pointer;
  white-space: nowrap;
}
.side-item.on {
  background: var(--brand-tint);
  color: var(--brand);
}
.side-item .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand);
}
.side-item .pill {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--brand);
  color: #fff;
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.plain-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.review {
  border-top: 1px dashed var(--border);
  padding-top: 12px;
  margin-top: 12px;
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
  max-width: 80%;
  white-space: pre-wrap;
}
.msg.mine .bubble {
  background: var(--brand-tint);
  border-color: #ffd9d9;
}
.guide {
  margin-top: 18px;
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  background: #fff8e6;
  color: #8a6d1f;
}
.ov-summary .kv {
  margin: 0;
}
.ov-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  border-left: 4px solid var(--brand);
}
.ov-action.warn {
  border-left-color: var(--warn, #d97706);
  background: #fff8e6;
}
.ov-action p {
  margin: 0;
  font-weight: 600;
}
.ov-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.ov-stat {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ov-stat b {
  font-size: 20px;
}
.ov-stat span {
  font-size: 12px;
  color: var(--ink-2);
}
@media (max-width: 520px) {
  .ov-stats {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
