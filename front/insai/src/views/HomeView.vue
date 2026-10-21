<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

// 로그인 상태면 바로 이동, 아니면 로그인 후 돌아오도록 안내한다.
function go(to) {
  if (auth.isAuthenticated) return router.push({ name: to })
  router.push({ name: 'login', query: { redirect: router.resolve({ name: to }).fullPath } })
}

function scrollTo(id) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

// 이용 안내 (히어로 카드)
const guide = [
  ['로그인 · 회원가입', '청구인은 가입 후 바로, 손해사정사는 관리자 승인 후 이용합니다.'],
  ['청구 건 만들기', '“청구 관리”에서 청구 유형을 골라 새 청구 건을 엽니다.'],
  ['요청 서류 제출', '“해야 할 일”에 쌓인 서류를 양식에 맞춰 작성하고 PDF를 올립니다.'],
  ['AI 서류 점검', '청구 건의 “AI 서류 점검”에서 PDF 3종을 골라 기재 상태를 확인합니다.'],
  ['결과 확인', '손해사정사의 사정 결과를 쉬운 말로 풀어 전달받습니다.'],
]
const notices = [
  'AI는 보험금 지급 여부를 결정하지 않습니다. 최종 판단은 손해사정사가 합니다.',
  'PDF 원본은 AI로 전송되지 않습니다. 필요한 항목만 추출해 점검합니다.',
  '스캔·사진 PDF는 글자를 읽을 수 없어 “확인 불가”로 표시될 수 있습니다.',
]

const steps = [
  ['역할 선택 · 내 보험 조회', '청구인/손해사정사로 입장하고, 청구인은 가입한 보험부터 확인합니다.'],
  ['서류 요청 확인', '손해사정사가 요청한 서류가 “해야 할 일”에 쌓입니다. 무엇을, 언제까지 낼지 한눈에.'],
  ['양식에 맞춰 작성·제출', '서류를 여는 대신 화면 안에서 칸을 채우고, 확인 후 제출합니다.'],
  ['AI 요약 + 손해사정사 검토', 'AI가 제출 내용을 구조화하고 누락을 짚어 사정사에게 전달합니다.'],
  ['사정 결과를 평문으로 확인', '결정 내용을 이해하기 쉬운 말로 풀어 청구인에게 전달합니다.'],
]

// 보험 종류 → 세부 청구 유형. (현재 실제 동작은 생명보험 흐름만)
const insuranceCategories = [
  {
    name: '생명보험',
    subtypes: ['사망보험금 (일반·재해)', '후유장해보험금', '진단보험금 (암·뇌·심장)', '입원·수술급여금'],
  },
  {
    name: '실손의료보험',
    subtypes: ['통원 의료비', '입원 의료비', '처방조제비', '비급여 (도수·주사·MRI)'],
  },
  {
    name: '자동차보험',
    subtypes: ['대인배상', '대물배상', '자기신체사고·자동차상해', '자기차량손해'],
  },
  {
    name: '상해·질병보험',
    subtypes: ['상해 진단·수술', '골절·화상 진단금', '질병 후유장해', '입원일당'],
  },
  {
    name: '화재·재물보험',
    subtypes: ['주택·건물 화재', '가재도구 손해', '일상생활 배상책임'],
  },
]

const values = [
  ['🧩', '입력을 양식 칸에 맞게', '청구인이 자유롭게 쓴 내용을 미리 저장된 양식의 알맞은 칸에 채워 넣습니다.'],
  ['🔍', '누락을 먼저 짚어', '필수 항목이 비어 있으면 제출 전에 표시하고, 사정사에게도 함께 알립니다.'],
  ['📝', '결정은 쉬운 말로', '손해사정사의 사정 결과를 청구인이 이해할 수 있는 평문으로 바꿔 전달합니다.'],
]

const faqs = [
  ['AI 서류 점검은 무엇을 보나요?', 'PDF에서 진단명·질병코드·날짜·병원명·금액 등을 읽어 빠진 항목과 문서 간 불일치를 짚습니다. 규칙으로 확인되면 AI를 호출하지 않습니다.'],
  ['어떤 보험을 다루나요?', '생명보험, 실손의료보험, 자동차보험, 상해·질병보험, 화재·재물보험까지 보험금 청구 전반을 다룹니다. 각 보험의 세부 청구 유형에 맞춰 서류를 안내합니다.'],
  ['서류 파일을 직접 올려야 하나요?', '텍스트 항목은 화면 안의 양식에서 바로 작성합니다. 첨부가 필요한 항목만 파일(PDF·이미지, 20MB 이하)을 올립니다.'],
  ['AI가 보험금 지급 여부를 결정하나요?', '아니요. AI는 정리·요약·번역만 돕고, 판단과 사정은 손해사정사가 합니다.'],
  ['내 보험 정보는 어디서 오나요?', '지금은 데모 데이터입니다. 정식 버전에서는 보험사 연동 또는 직접 등록으로 채워집니다.'],
]
const openFaq = ref(0)
</script>

<template>
  <div class="lp">
    <!-- 상단 바 -->
    <header class="lp-bar">
      <div class="lp-bar-in">
        <span class="brand-word" style="font-size: 20px">Insurance<b>AI</b></span>
        <nav class="lp-bar-nav">
          <button @click="scrollTo('guide')">이용 안내</button>
          <button @click="scrollTo('how')">진행 절차</button>
          <button @click="scrollTo('areas')">청구 유형</button>
          <button @click="scrollTo('faq')">자주 묻는 질문</button>
        </nav>
        <div class="grow" />
        <RouterLink v-if="!auth.isAuthenticated" to="/login" class="lp-login">로그인</RouterLink>
        <button class="btn" @click="go('cases')">시작하기</button>
      </div>
    </header>

    <!-- 히어로 -->
    <section class="lp-hero">
      <div class="lp-hero-in">
        <div class="lp-hero-copy">
          <span class="lp-eyebrow">보험금 청구 협업 플랫폼</span>
          <h1>보험금 청구,<br />청구인과 손해사정사가<br />한 화면에서</h1>
          <p>
            생명보험·실손·자동차·상해까지, 서류 요청 · 작성 · 검토 · 사정을 하나의 공간에서.
            그 사이를 AI가 중개해 옮겨 적고, 빠진 곳을 짚고, 결정을 쉬운 말로 풀어 줍니다.
          </p>
          <div class="lp-hero-actions">
            <button class="btn lg" @click="go('policies')">
              🛡️ 내 보험 조회하기
            </button>
            <button class="btn lg ghost" @click="scrollTo('how')">
              어떻게 진행되나요
            </button>
          </div>
        </div>

        <!-- 이용 안내 카드 -->
        <div id="guide" class="lp-guide">
          <h3>이용 안내</h3>
          <ol class="lp-guide-steps">
            <li v-for="(g, i) in guide" :key="i">
              <span class="lp-guide-no">{{ i + 1 }}</span>
              <div>
                <b>{{ g[0] }}</b>
                <p class="small muted">{{ g[1] }}</p>
              </div>
            </li>
          </ol>
          <ul class="lp-guide-notes small">
            <li v-for="n in notices" :key="n">{{ n }}</li>
          </ul>
          <RouterLink v-if="!auth.isAuthenticated" to="/login" class="btn block">로그인하고 시작하기</RouterLink>
          <button v-else class="btn block" @click="go('cases')">내 청구 건으로 이동</button>
        </div>
      </div>
    </section>

    <!-- 지표 스트립 -->
    <section class="lp-stats">
      <div class="lp-stats-in">
        <div><b>5개 보종</b><span>생명 · 실손 · 자동차 · 상해 · 화재</span></div>
        <div><b>18+종</b><span>청구 유형별 서류 양식</span></div>
        <div><b>4단계</b><span>요청 → 작성 → 검토 → 사정</span></div>
        <div><b>AI</b><span>요약 · 누락 점검 · 평문 변환</span></div>
      </div>
    </section>

    <!-- 진행 절차 -->
    <section id="how" class="lp-section">
      <div class="lp-inner">
        <div class="lp-head">
          <span class="lp-eyebrow dark">진행 절차</span>
          <h2>다섯 걸음이면 됩니다</h2>
        </div>
        <ol class="lp-steps">
          <li v-for="(s, i) in steps" :key="i">
            <span class="lp-step-no">{{ i + 1 }}</span>
            <div>
              <h3>{{ s[0] }}</h3>
              <p class="muted small">{{ s[1] }}</p>
            </div>
          </li>
        </ol>
      </div>
    </section>

    <!-- 보험 종류 · 청구 유형 -->
    <section id="areas" class="lp-section alt">
      <div class="lp-inner">
        <div class="lp-head">
          <span class="lp-eyebrow dark">보험 종류</span>
          <h2>보험 종류별로 청구를 안내합니다</h2>
          <p class="muted small" style="margin-top: 6px">
            생명보험부터 실손·자동차·상해·화재까지, 청구 유형에 맞춰 필요한 서류를 정리해 드립니다.
          </p>
        </div>
        <div class="grid">
          <div v-for="c in insuranceCategories" :key="c.name" class="cat card stack">
            <h3>{{ c.name }}</h3>
            <ul class="cat-subs">
              <li v-for="s in c.subtypes" :key="s">{{ s }}</li>
            </ul>
          </div>
        </div>
      </div>
    </section>

    <!-- 가치 -->
    <section class="lp-section">
      <div class="lp-inner">
        <div class="lp-head">
          <span class="lp-eyebrow dark">AI 중개</span>
          <h2>사람의 일은 사람이, 옮겨 적는 일은 AI가</h2>
        </div>
        <div class="grid">
          <div v-for="v in values" :key="v[1]" class="card stack" style="gap: 8px">
            <div style="font-size: 24px">{{ v[0] }}</div>
            <h3>{{ v[1] }}</h3>
            <p class="muted small">{{ v[2] }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- FAQ -->
    <section id="faq" class="lp-section alt">
      <div class="lp-inner" style="max-width: 720px">
        <div class="lp-head">
          <span class="lp-eyebrow dark">자주 묻는 질문</span>
          <h2>궁금한 점</h2>
        </div>
        <div class="lp-faq">
          <div v-for="(f, i) in faqs" :key="i" class="lp-faq-item">
            <button class="lp-faq-q" @click="openFaq = openFaq === i ? -1 : i">
              <span>{{ f[0] }}</span>
              <span class="lp-faq-chev" :class="{ up: openFaq === i }">▾</span>
            </button>
            <p v-if="openFaq === i" class="lp-faq-a muted small">{{ f[1] }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- 마무리 CTA -->
    <section class="lp-cta">
      <div class="lp-inner" style="text-align: center">
        <h2>지금 바로 둘러보세요</h2>
        <p class="small">청구인은 회원가입 후 바로, 손해사정사는 관리자 승인 후 이용할 수 있습니다.</p>
        <div class="lp-cta-btns">
          <button class="btn lg" @click="go('cases')">🧾 로그인하고 시작하기</button>
          <RouterLink v-if="!auth.isAuthenticated" to="/signup" class="btn lg ghost">회원가입</RouterLink>
        </div>
      </div>
    </section>

    <footer class="lp-foot">
      <div class="lp-inner lp-foot-in">
        <span class="brand-word">Insurance<b>AI</b></span>
        <p class="small muted">AI 중립 보험금 청구 협업 공간 · 데모 서비스</p>
        <p class="small muted">© {{ new Date().getFullYear() }} InsuranceAI</p>
      </div>
    </footer>

    <!-- 모바일 하단 고정 바 -->
    <div class="lp-dock">
      <button @click="go('CLAIMANT', 'policies')">내 보험</button>
      <button @click="go('CLAIMANT', 'cases')">청구인 시작</button>
      <button class="prim" @click="go('ADJUSTER', 'cases')">사정사 시작</button>
    </div>
  </div>
</template>

<style scoped>
.lp {
  color: var(--ink);
}
.lp-inner {
  max-width: 1000px;
  margin: 0 auto;
  padding: 0 20px;
}

/* 상단 바 */
.lp-bar {
  position: sticky;
  top: 0;
  z-index: 30;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--border);
}
.lp-bar-in {
  max-width: 1000px;
  margin: 0 auto;
  height: 60px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 22px;
}
.lp-bar-nav {
  display: flex;
  gap: 18px;
}
.lp-bar-nav button {
  border: 0;
  background: none;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-2);
}
.lp-bar-nav button:hover {
  color: var(--brand);
}
.lp-login {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-2);
  padding: 0 6px;
}

/* 히어로 */
.lp-hero {
  background: linear-gradient(160deg, var(--brand-deep) 0%, var(--brand-deep-2) 100%);
  color: #fff;
}
.lp-hero-in {
  max-width: 1000px;
  margin: 0 auto;
  padding: 68px 20px 76px;
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 44px;
  align-items: center;
}
.lp-eyebrow {
  display: inline-block;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.14);
  color: #d6fbf5;
  margin-bottom: 16px;
}
.lp-eyebrow.dark {
  background: var(--brand-tint);
  color: var(--brand-deep);
  margin-bottom: 10px;
}
.lp-hero-copy h1 {
  font-size: 38px;
  line-height: 1.25;
  letter-spacing: -0.02em;
}
.lp-hero-copy p {
  margin-top: 18px;
  color: rgba(255, 255, 255, 0.86);
  font-size: 15px;
  max-width: 460px;
}
.lp-hero-actions {
  display: flex;
  gap: 10px;
  margin-top: 26px;
  flex-wrap: wrap;
}
.btn.lg {
  height: 48px;
  padding: 0 22px;
  font-size: 15px;
}
.lp-hero-actions .btn.ghost {
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  border-color: rgba(255, 255, 255, 0.65);
}
.lp-hero-actions .btn.ghost:hover {
  background: rgba(255, 255, 255, 0.26);
}

/* 이용 안내 카드 */
.lp-guide {
  background: #fff;
  border-radius: 16px;
  padding: 24px;
  color: var(--ink);
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.25);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.lp-guide-steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.lp-guide-steps li {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}
.lp-guide-steps b {
  font-size: 14px;
}
.lp-guide-no {
  flex: none;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--brand-tint);
  color: var(--brand-deep);
  font-size: 12px;
  font-weight: 700;
  display: grid;
  place-items: center;
}
.lp-guide-notes {
  margin: 0;
  padding: 10px 12px 10px 28px;
  background: var(--surface);
  border-radius: 10px;
  color: var(--ink-2);
}
.lp-guide-notes li + li {
  margin-top: 4px;
}

/* 지표 */
.lp-stats {
  background: var(--brand-tint);
  border-bottom: 1px solid var(--border);
}
.lp-stats-in {
  max-width: 1000px;
  margin: 0 auto;
  padding: 26px 20px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}
.lp-stats-in div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.lp-stats-in b {
  font-size: 22px;
  color: var(--brand-deep);
}
.lp-stats-in span {
  font-size: 13px;
  color: var(--ink-2);
}

/* 섹션 */
.lp-section {
  padding: 64px 0;
  background: #fff;
}
.lp-section.alt {
  background: var(--surface);
}
.lp-head {
  margin-bottom: 28px;
}
.lp-head h2 {
  font-size: 26px;
  letter-spacing: -0.02em;
}

/* 절차 */
.lp-steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 14px;
}
.lp-steps li {
  display: flex;
  gap: 16px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 18px 20px;
}
.lp-section.alt .lp-steps li {
  background: #fff;
}
.lp-step-no {
  flex: none;
  width: 30px;
  height: 30px;
  border-radius: 999px;
  background: var(--brand);
  color: #fff;
  font-weight: 800;
  font-size: 14px;
  display: grid;
  place-items: center;
}

/* 보험 종류 카드 */
.cat {
  gap: 12px;
}
.cat-subs {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.cat-subs li {
  font-size: 13px;
  color: var(--ink-2);
  padding-left: 14px;
  position: relative;
}
.cat-subs li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  width: 5px;
  height: 5px;
  border-radius: 999px;
  background: var(--brand);
}

/* FAQ */
.lp-faq {
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--border);
}
.lp-faq-item {
  border-bottom: 1px solid var(--border);
}
.lp-faq-q {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 16px 4px;
  background: none;
  border: 0;
  cursor: pointer;
  font-size: 15px;
  font-weight: 600;
  text-align: left;
  color: var(--ink);
}
.lp-faq-chev {
  transition: transform 0.15s;
  color: var(--muted);
}
.lp-faq-chev.up {
  transform: rotate(180deg);
}
.lp-faq-a {
  padding: 0 4px 16px;
  line-height: 1.6;
}

/* 마무리 CTA */
.lp-cta {
  padding: 64px 0;
  background: linear-gradient(160deg, var(--brand-deep) 0%, var(--brand-deep-2) 100%);
  color: #fff;
}
.lp-cta h2 {
  font-size: 26px;
}
.lp-cta p {
  margin-top: 8px;
  color: rgba(255, 255, 255, 0.82);
}
.lp-cta-btns {
  margin-top: 22px;
  display: flex;
  gap: 10px;
  justify-content: center;
  flex-wrap: wrap;
}
.lp-cta-btns .btn.ghost {
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  border-color: rgba(255, 255, 255, 0.65);
}
.lp-cta-btns .btn.ghost:hover {
  background: rgba(255, 255, 255, 0.26);
}

/* 푸터 */
.lp-foot {
  background: var(--brand-deep-2);
  color: #fff;
  padding: 34px 0;
}
.lp-foot-in {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.lp-foot .brand-word b {
  color: #7fe6db;
}
.lp-foot .muted {
  color: rgba(255, 255, 255, 0.6);
}

/* 모바일 하단 고정 바 */
.lp-dock {
  display: none;
}

@media (max-width: 860px) {
  .lp-hero-in {
    grid-template-columns: 1fr;
    padding: 48px 20px 56px;
  }
  .lp-hero-copy h1 {
    font-size: 30px;
  }
  .lp-bar-nav {
    display: none;
  }
  .lp-stats-in {
    grid-template-columns: 1fr 1fr;
  }
  .lp-dock {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 40;
    display: flex;
    gap: 8px;
    padding: 10px 12px;
    background: #fff;
    border-top: 1px solid var(--border);
    box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.08);
  }
  .lp-dock button {
    flex: 1;
    height: 42px;
    border-radius: 8px;
    border: 1px solid var(--border);
    background: #fff;
    font-weight: 700;
    font-size: 13px;
    cursor: pointer;
  }
  .lp-dock button.prim {
    background: var(--brand);
    color: #fff;
    border-color: var(--brand);
  }
}
</style>
