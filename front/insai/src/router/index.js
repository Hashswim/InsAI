import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
    meta: { public: true },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/signup',
    name: 'signup',
    component: () => import('@/views/SignupView.vue'),
    meta: { public: true },
  },
  { path: '/policies', name: 'policies', component: () => import('@/views/PoliciesView.vue') },
  { path: '/cases', name: 'cases', component: () => import('@/views/CasesView.vue') },
  { path: '/cases/new', name: 'case-new', component: () => import('@/views/NewCaseView.vue') },
  {
    path: '/cases/:id',
    name: 'case-detail',
    component: () => import('@/views/CaseDetailView.vue'),
    props: true,
  },
  {
    path: '/cases/:id/requests/:docRequestId/submit',
    name: 'submit-form',
    component: () => import('@/views/SubmitFormView.vue'),
    props: true,
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (['login', 'signup'].includes(to.name) && auth.isAuthenticated) {
    return { name: 'cases' }
  }
})

export default router
