<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const username = ref('')
const email = ref('')
const password = ref('')
const loading = ref(false)
const error = ref<string | null>(null)

async function submit() {
  loading.value = true
  error.value = null
  try {
    await auth.register({
      username: username.value,
      email: email.value || undefined,
      password: password.value,
    })
    router.push('/books')
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Не удалось зарегистрироваться'
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
          <v-card-title>Регистрация</v-card-title>
          <v-card-text>
            <v-form @submit.prevent="submit">
              <v-text-field v-model="username" label="Имя пользователя (3-64)" />
              <v-text-field v-model="email" label="Email (необязательно)" type="email" />
              <v-text-field v-model="password" label="Пароль (8-128)" type="password" />
              <v-alert v-if="error" type="error" variant="tonal" class="mb-2">{{ error }}</v-alert>
              <v-btn type="submit" color="primary" block :loading="loading">Зарегистрироваться</v-btn>
            </v-form>
            <div class="text-center mt-4">
              Уже есть аккаунт?
              <router-link to="/login">Вход</router-link>
            </div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>
