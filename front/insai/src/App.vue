<script setup>
import { useAuthStore } from '@/stores/auth'
import AppHeader from '@/components/AppHeader.vue'

const auth = useAuthStore()
</script>

<template>
  <AppHeader v-if="auth.isAuthenticated" />
  <main class="page">
    <RouterView />
  </main>
</template>
