import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/',
    name: 'landing',
    component: () => import('@/views/LandingView.vue'),
    meta: { public: true },
  },
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
    return { name: 'landing' }
  }
  if (to.name === 'landing' && auth.isAuthenticated) {
    return { name: 'cases' }
  }
})

export default router
