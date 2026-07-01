<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const router = useRouter()
const auth = useAuthStore()

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <v-app-bar color="primary" density="comfortable">
    <v-app-bar-nav-icon @click="emit('update:modelValue', !modelValue)" />
    <v-app-bar-title>Библиотека книг</v-app-bar-title>
    <v-spacer />
    <span v-if="auth.user" class="mr-4">{{ auth.user.username }}</span>
    <v-btn icon="mdi-logout" title="Выйти" @click="logout" />
  </v-app-bar>
</template>
