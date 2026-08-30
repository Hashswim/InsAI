<script setup>
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import * as casesApi from '@/api/cases'
import * as formsApi from '@/api/forms'
import { claimTypes, requiredDocuments } from '@/api/catalog'
import { isRead, markRead } from '@/lib/readState'
import StatusBadge from '@/components/StatusBadge.vue'
import CaseTimeline from '@/components/CaseTimeline.vue'
import FeedCard from '@/components/FeedCard.vue'

const props = defineProps({ id: { type: String, required: true } })
const auth = useAuthStore()

const caseData = ref(null)
const err = ref('')
const nav = ref('requests')
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
const canTakeCase = computed(
  () => !caseData.value?.adjusterId && auth.isAdjuster && caseData.value?.claimantId !== auth.user?.id,
)
const caseNo = computed(() => (caseData.value ? caseData.value.id.replace(/-/g, '').slice(0, 10).toUpperCase() : ''))

function fmtDate(at) {
  return at ? new Date(at).toLocaleDateString('ko-KR') : ''
}
function fmtDateTime(at) {
  return at ? new Date(at).toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }) : ''
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
  await Promise.all([loadDocs(), loadMessages(), loadTimeline()])
})

async function takeCase() {
  try {
    await casesApi.assignAdjuster(props.id, auth.user.id)
    await Promise.all([loadCase(), loadTimeline()])
  } catch (e) {
    err.value = e.message
  }
}

/* ---------- 요청 피드 ---------- */
const requestFeed = computed(() =>
  [...docs.value]
    .sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))
    .map((d) => ({
      ...d,
      key: `req-${d.id}`,
      isNew: d.status === 'PENDING' && !isReadKey(`req-${d.id}`),
    })),
)
function isReadKey(k) {
  readTick.value // 의존성
  return isRead(props.id, k)
}
function acknowledge(k) {
  markRead(props.id, k)
  readTick.value++
}

/* 서류 요청 추가 (사정사) */
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

/* 제출물 검토 (사정사) */
const submissions = ref({}) // drId -> submission | 'none'
async function loadSubmission(drId) {
  if (submissions.value[drId]) return
  try {
    submissions.value = { ...submissions.value, [drId]: await formsApi.getSubmissionByDocRequest(drId) }
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
    await loadDocs()
  } catch (e) {
    err.value = e.message
  }
}

/* ---------- 안내 피드 ---------- */
const noticeFeed = computed(() => {
  const items = messages.value.map((m) => ({
    key: `msg-${m.id}`,
    kind: m.senderRole === 'ADJUSTER' ? 'notice' : 'inquiry',
    title: m.senderRole === 'ADJUSTER' ? '손해사정사 안내' : m.senderId === auth.user?.id ? '내 문의' : '문의',
    date: m.createdAt,
    body: m.body,
  }))
  if (caseData.value && ['DECISION_MADE', 'CLOSED'].includes(caseData.value.status)) {
    items.push({
      key: 'decision',
      kind: 'notice',
      title: '사정 결과 등록',
      date: null,
      body: '손해사정사가 사정 결과를 등록했습니다. 상세 내용은 문의로 확인해 주세요.',
    })
  }
  return items.sort((a, b) => new Date(b.date || Date.now()) - new Date(a.date || Date.now()))
})

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

/* ---------- 사정 결과 (사정사) ---------- */
const decisionText = ref('')
const decisionErr = ref('')
const decisionSaved = ref(false)
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
    decisionSaved.value = true
    await Promise.all([loadCase(), loadTimeline()])
  } catch (e) {
    decisionErr.value = e.message
  }
}

function goNav(n) {
  nav.value = n
  if (n === 'notices') loadMessages()
  if (n === 'timeline') loadTimeline()
}

const NAV = [
  { k: 'requests', label: '요청', icon: '📄' },
  { k: 'notices', label: '안내', icon: '🔔' },
  { k: 'timeline', label: '타임라인', icon: '🗂' },
  { k: 'result', label: '사정 결과', icon: '✅' },
]
const roleLabel = { CLAIMANT: '청구인', ADJUSTER: '담당 손해사정사' }
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
          </div>
          <nav>
            <button
              v-for="n in NAV"
              :key="n.k"
              class="side-item"
              :class="{ on: nav === n.k }"
              @click="goNav(n.k)"
            >
              <span>{{ n.icon }}</span> {{ n.label }}
              <span
                v-if="n.k === 'requests' && requestFeed.some((r) => r.isNew)"
                class="dot"
              />
              <span
                v-if="n.k === 'notices' && noticeFeed.some((i) => i.kind === 'notice' && !isReadKey(i.key))"
                class="dot"
              />
            </button>
          </nav>
        </aside>

        <!-- 본문 -->
        <div class="main stack">
          <div class="card stack" style="gap: 8px">
            <div class="between">
              <StatusBadge :status="caseData.status" />
              <span v-if="myRole" class="badge b-muted">{{ roleLabel[myRole] }}</span>
            </div>
            <h1>{{ caseData.title }}</h1>
            <div class="row small muted">
              <span>개설 {{ fmtDate(caseData.createdAt) }}</span>
              <span>·</span>
              <span>사정사 {{ caseData.adjusterId ? '배정됨' : '미배정' }}</span>
            </div>
            <button v-if="canTakeCase" class="btn sm" @click="takeCase">이 청구 배정받기</button>
          </div>

          <!-- 요청 -->
          <section v-if="nav === 'requests'" class="stack">
            <div class="between">
              <h2>요청 <span class="muted small">({{ docs.length }})</span></h2>
              <button
                v-if="myRole === 'ADJUSTER'"
                class="btn sm ghost"
                @click="adding ? (adding = false) : openAdd()"
              >
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

            <p v-if="!docs.length" class="card muted">아직 요청된 서류가 없습니다.</p>

            <FeedCard
              v-for="d in requestFeed"
              :key="d.key"
              accent="brand"
              :title="d.title"
              :date="fmtDate(d.createdAt)"
              :is-new="d.isNew"
              attachment
              @open="acknowledge(d.key)"
            >
              <div class="row" style="gap: 6px; margin-bottom: 8px">
                <StatusBadge :status="d.status" />
                <span v-if="d.dueDate" class="small muted">제출 기한 {{ d.dueDate }}</span>
              </div>
              <div v-if="d.rejectionReason" class="alert err" style="margin-bottom: 8px">
                반려 사유: {{ d.rejectionReason }}
              </div>
              <p class="muted">
                {{
                  myRole === 'CLAIMANT'
                    ? '아래 버튼에서 서류의 칸을 채워 제출하세요.'
                    : '청구인이 제출하면 여기에서 검토할 수 있습니다.'
                }}
              </p>

              <template #actions>
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
                  @click="loadSubmission(d.id)"
                >
                  제출 내용 불러오기
                </button>
              </template>

              <div v-if="myRole === 'ADJUSTER' && submissions[d.id] && submissions[d.id] !== 'none'" class="review">
                <div class="row" style="gap: 6px">
                  <span class="badge" :class="submissions[d.id].complete ? 'b-ok' : 'b-warn'">
                    {{ submissions[d.id].complete ? '완료' : '미완' }}
                  </span>
                  <span class="small muted">버전 {{ submissions[d.id].version }}</span>
                </div>
                <dl class="kv" style="margin-top: 10px">
                  <template v-for="(v, k) in submissions[d.id].data" :key="k">
                    <dt>{{ k }}</dt>
                    <dd>{{ v }}</dd>
                  </template>
                </dl>
                <div
                  v-if="submissions[d.id].missingFields?.length"
                  class="alert"
                  style="background: #fff4e5; color: var(--warn); margin-top: 8px"
                >
                  누락 필드: {{ submissions[d.id].missingFields.join(', ') }}
                </div>
                <div class="row" style="margin-top: 10px">
                  <button class="btn sm" @click="decide(d.id, 'ACCEPT')">승인</button>
                  <button class="btn sm danger" @click="decide(d.id, 'REJECT')">반려</button>
                </div>
              </div>
              <p v-else-if="submissions[d.id] === 'none'" class="small muted" style="margin-top: 8px">
                아직 제출된 내용이 없습니다.
              </p>
            </FeedCard>
          </section>

          <!-- 안내 -->
          <section v-else-if="nav === 'notices'" class="stack">
            <h2>안내 <span class="muted small">({{ noticeFeed.length }})</span></h2>
            <p v-if="!noticeFeed.length" class="card muted">아직 안내가 없습니다.</p>

            <FeedCard
              v-for="i in noticeFeed"
              :key="i.key"
              :accent="i.kind === 'inquiry' ? 'muted' : 'indigo'"
              :title="i.title"
              :date="fmtDateTime(i.date)"
              :is-new="i.kind === 'notice' && !isReadKey(i.key)"
            >
              {{ i.body }}
              <template v-if="i.kind === 'notice' && !isReadKey(i.key)" #actions>
                <button class="btn sm ghost" @click="acknowledge(i.key)">확인 완료</button>
              </template>
            </FeedCard>

            <form v-if="myRole" class="card stack" @submit.prevent="sendInquiry">
              <div class="field">
                <label>{{ myRole === 'CLAIMANT' ? '문의 남기기' : '안내 보내기' }}</label>
                <textarea v-model="inquiryText" class="textarea" placeholder="내용을 입력하세요" />
              </div>
              <div v-if="inquiryErr" class="alert err">{{ inquiryErr }}</div>
              <button class="btn sm" type="submit" :disabled="!inquiryText.trim()">보내기</button>
            </form>
          </section>

          <!-- 타임라인 -->
          <section v-else-if="nav === 'timeline'">
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
                <p v-if="decisionSaved && decisionText" class="small" style="margin-top: 10px; white-space: pre-wrap">
                  {{ decisionText }}
                </p>
              </div>
              <div v-else class="card muted">서류 제출이 완료되면 심사를 진행할 수 있습니다.</div>
            </template>
            <template v-else>
              <div class="card muted">
                <template v-if="caseData.status === 'UNDER_REVIEW'">손해사정사가 서류를 심사하고 있습니다.</template>
                <template v-else-if="['DECISION_MADE', 'CLOSED'].includes(caseData.status)">
                  사정 결과가 등록되었습니다. 자세한 내용은 “안내” 탭 또는 담당 손해사정사에게 문의하세요.
                </template>
                <template v-else>아직 사정 결과가 없습니다.</template>
              </div>
            </template>
          </section>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.detail {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: 20px;
  align-items: start;
}
@media (max-width: 760px) {
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
</style>
