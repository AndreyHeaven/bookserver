<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { adminApi, type AdminUser } from '@/api/admin'

const activeTab = ref('settings')
const users = ref<AdminUser[]>([])
const query = ref('')
const page = ref(1)
const totalPages = ref(1)
const registrationEnabled = ref(true)
const loading = ref(false)
const generatedPassword = ref<string | null>(null)
const passwordDialogOpen = ref(false)
const error = ref<string | null>(null)

async function loadUsers() {
  loading.value = true
  error.value = null
  try {
    const { data } = await adminApi.users(query.value, page.value - 1)
    users.value = data.content
    totalPages.value = Math.max(data.page.totalPages, 1)
  } catch {
    error.value = 'Не удалось загрузить пользователей'
  } finally {
    loading.value = false
  }
}

async function saveSettings() {
  const { data } = await adminApi.updateSettings(registrationEnabled.value)
  registrationEnabled.value = data.registrationEnabled
}

async function toggle(user: AdminUser) {
  await adminApi.setEnabled(user.id, !user.enabled)
  await loadUsers()
}

async function remove(user: AdminUser) {
  if (!confirm(`Удалить пользователя ${user.username}?`)) return
  await adminApi.deleteUser(user.id)
  await loadUsers()
}

async function resetPassword(user: AdminUser) {
  const { data } = await adminApi.resetPassword(user.id)
  generatedPassword.value = data.password
  passwordDialogOpen.value = true
}

async function copyPassword() {
  if (generatedPassword.value) await navigator.clipboard.writeText(generatedPassword.value)
}

watch(page, loadUsers)
onMounted(async () => {
  const { data } = await adminApi.settings()
  registrationEnabled.value = data.registrationEnabled
  await loadUsers()
})
</script>

<template>
  <div>
    <h1 class="text-h5 mb-4">Администрирование</h1>

    <v-tabs v-model="activeTab" color="primary" class="mb-4">
      <v-tab value="settings" prepend-icon="mdi-cog">Настройки сайта</v-tab>
      <v-tab value="users" prepend-icon="mdi-account-group">Пользователи</v-tab>
    </v-tabs>

    <v-window v-model="activeTab">
      <v-window-item value="settings">
        <v-card>
          <v-card-title>Настройки сайта</v-card-title>
          <v-card-text>
            <v-switch v-model="registrationEnabled" label="Разрешить публичную регистрацию" color="primary" hide-details />
          </v-card-text>
          <v-card-actions><v-btn color="primary" @click="saveSettings">Сохранить</v-btn></v-card-actions>
        </v-card>
      </v-window-item>

      <v-window-item value="users">
        <v-card>
          <v-card-title class="d-flex align-center ga-3 flex-wrap">
            <span>Пользователи</span>
            <v-text-field v-model="query" density="compact" hide-details label="Поиск по имени" style="max-width: 280px" @keyup.enter="page = 1; loadUsers()" />
            <v-btn icon="mdi-magnify" variant="text" @click="page = 1; loadUsers()" />
          </v-card-title>
          <v-alert v-if="error" type="error" class="ma-3">{{ error }}</v-alert>
          <v-progress-linear v-if="loading" indeterminate />
          <v-table>
            <thead><tr><th>Пользователь</th><th>Роли</th><th>Статус</th><th>Действия</th></tr></thead>
            <tbody>
              <tr v-for="user in users" :key="user.id">
                <td>{{ user.username }}<div class="text-caption">{{ user.email }}</div></td>
                <td>{{ user.roles.join(', ') }}</td>
                <td>{{ user.enabled ? 'Активен' : 'Отключён' }}</td>
                <td class="text-no-wrap">
                  <v-btn size="small" variant="text" @click="toggle(user)">{{ user.enabled ? 'Отключить' : 'Включить' }}</v-btn>
                  <v-btn size="small" variant="text" @click="resetPassword(user)">Сбросить пароль</v-btn>
                  <v-btn size="small" color="error" variant="text" @click="remove(user)">Удалить</v-btn>
                </td>
              </tr>
            </tbody>
          </v-table>
          <v-pagination v-model="page" :length="totalPages" class="my-3" />
        </v-card>
      </v-window-item>
    </v-window>

    <v-dialog v-model="passwordDialogOpen" max-width="480">
      <v-card>
        <v-card-title>Новый пароль</v-card-title>
        <v-card-text>
          <p>Скопируйте пароль сейчас: он больше не будет показан.</p>
          <v-text-field :model-value="generatedPassword" readonly append-inner-icon="mdi-content-copy" @click:append-inner="copyPassword" />
        </v-card-text>
        <v-card-actions><v-spacer /><v-btn @click="passwordDialogOpen = false; generatedPassword = null">Закрыть</v-btn></v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
