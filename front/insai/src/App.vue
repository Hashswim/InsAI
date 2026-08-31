<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppHeader from '@/components/AppHeader.vue'

const auth = useAuthStore()
const route = useRoute()
const isLanding = computed(() => route.name === 'home')
</script>

<template>
  <AppHeader v-if="auth.isAuthenticated && !isLanding" />
  <main :class="isLanding ? 'bleed' : 'page'">
    <RouterView />
  </main>
</template>

<style scoped>
.bleed {
  width: 100%;
}
</style>
