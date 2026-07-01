<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref<string | null>(null)

async function submit() {
  loading.value = true
  error.value = null
  try {
    await auth.login({ username: username.value, password: password.value })
    const redirect = (route.query.redirect as string) || '/books'
    router.push(redirect)
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Не удалось войти'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <v-container class="fill-height" fluid>
    <v-row justify="center" align="center">
      <v-col cols="12" sm="8" md="4">
        <v-card>
          <v-card-title>Вход</v-card-title>
          <v-card-text>
            <v-form @submit.prevent="submit">
              <v-text-field v-model="username" label="Имя пользователя" autocomplete="username" />
              <v-text-field
                v-model="password"
                label="Пароль"
                type="password"
                autocomplete="current-password"
              />
              <v-alert v-if="error" type="error" variant="tonal" class="mb-2">{{ error }}</v-alert>
              <v-btn type="submit" color="primary" block :loading="loading">Войти</v-btn>
            </v-form>
            <div class="text-center mt-4">
              Нет аккаунта?
              <router-link to="/register">Регистрация</router-link>
            </div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>
