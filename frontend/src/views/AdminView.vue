<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { adminApi, type AdminUser } from '@/api/admin'

const activeTab = ref('settings')
const users = ref<AdminUser[]>([])
const query = ref('')
const page = ref(1)
const totalPages = ref(1)
const registrationEnabled = ref(true)
const historyRetentionDays = ref(365)
const historyMaxEntries = ref(100)
const loading = ref(false)
const generatedPassword = ref<string | null>(null)
const passwordDialogOpen = ref(false)
const error = ref<string | null>(null)
const historyDialogOpen = ref(false)
const historyUser = ref<AdminUser | null>(null)
const historyEntries = ref<{ id: number; bookId: number; title: string; coverUrl: string; viewedAt: string }[]>([])
const historyPage = ref(1)
const historyTotalPages = ref(1)

const roleLabels: Record<string, string> = {
  ROLE_ADMIN: 'Администратор',
  ROLE_USER: 'Пользователь',
}

function formatRoles(roles: string[]): string {
  return roles.map((role) => roleLabels[role] ?? role).join(', ')
}

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
  const { data } = await adminApi.updateSettings({
    registrationEnabled: registrationEnabled.value,
    historyRetentionDays: historyRetentionDays.value,
    historyMaxEntries: historyMaxEntries.value,
  })
  registrationEnabled.value = data.registrationEnabled
  historyRetentionDays.value = data.historyRetentionDays
  historyMaxEntries.value = data.historyMaxEntries
}

async function openHistory(user: AdminUser) {
  historyUser.value = user
  historyPage.value = 1
  historyDialogOpen.value = true
  await loadUserHistory()
}

async function loadUserHistory() {
  if (!historyUser.value) return
  const { data } = await adminApi.userHistory(historyUser.value.id, historyPage.value - 1)
  historyEntries.value = data.content
  historyTotalPages.value = Math.max(data.page.totalPages, 1)
}

watch(historyPage, loadUserHistory)

async function toggle(user: AdminUser) {
  await adminApi.setEnabled(user.id, !user.enabled)
  await loadUsers()
}

async function remove(user: AdminUser) {
  if (!confirm(`Удалить пользователя ${user.username}?`)) return
  await adminApi.deleteUser(user.id)
  await loadUsers()
}

async function saveTelegramUid(user: AdminUser, value: string) {
  const telegramUid = value.trim() === '' ? null : Number(value)
  if (telegramUid !== null && (!Number.isSafeInteger(telegramUid) || telegramUid <= 0)) {
    error.value = 'UID Telegram должен быть положительным целым числом'
    return
  }
  await adminApi.setTelegramUid(user.id, telegramUid)
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
  historyRetentionDays.value = data.historyRetentionDays
  historyMaxEntries.value = data.historyMaxEntries
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
            <v-row class="mt-2">
              <v-col cols="12" sm="6">
                <v-text-field v-model.number="historyRetentionDays" type="number" label="Хранить историю просмотров, дней" min="1" />
              </v-col>
              <v-col cols="12" sm="6">
                <v-text-field v-model.number="historyMaxEntries" type="number" label="Максимум записей истории на пользователя" min="1" />
              </v-col>
            </v-row>
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
            <thead><tr><th>Пользователь</th><th>Telegram UID</th><th>Роли</th><th>Статус</th><th>Действия</th></tr></thead>
            <tbody>
              <tr v-for="user in users" :key="user.id">
                <td>{{ user.username }}<div class="text-caption">{{ user.email }}</div></td>
                <td>
                  <v-text-field
                    :model-value="user.telegramUid?.toString() ?? ''"
                    density="compact"
                    hide-details
                    label="UID"
                    style="min-width: 150px"
                    @update:model-value="saveTelegramUid(user, String($event))"
                  />
                </td>
                <td>{{ formatRoles(user.roles) }}</td>
                <td>
                  <v-icon
                    :color="user.enabled ? 'success' : 'error'"
                    :icon="user.enabled ? 'mdi-check-circle' : 'mdi-close-circle'"
                    :title="user.enabled ? 'Активен' : 'Отключён'"
                  />
                </td>
                <td class="text-no-wrap">
                  <v-btn
                    size="small"
                    variant="text"
                    :icon="user.enabled ? 'mdi-account-off' : 'mdi-account-check'"
                    :title="user.enabled ? 'Отключить' : 'Включить'"
                    @click="toggle(user)"
                  />
                  <v-btn
                    size="small"
                    variant="text"
                    icon="mdi-history"
                    title="История просмотров"
                    @click="openHistory(user)"
                  />
                  <v-btn
                    size="small"
                    variant="text"
                    icon="mdi-lock-reset"
                    title="Сбросить пароль"
                    @click="resetPassword(user)"
                  />
                  <v-btn
                    size="small"
                    color="error"
                    variant="text"
                    icon="mdi-delete"
                    title="Удалить"
                    @click="remove(user)"
                  />
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

    <v-dialog v-model="historyDialogOpen" max-width="640">
      <v-card>
        <v-card-title>История просмотров: {{ historyUser?.username }}</v-card-title>
        <v-list>
          <v-list-item v-for="entry in historyEntries" :key="entry.id" :title="entry.title" :subtitle="new Date(entry.viewedAt).toLocaleString()" />
        </v-list>
        <v-pagination v-model="historyPage" :length="historyTotalPages" class="my-3" />
        <v-card-actions><v-spacer /><v-btn @click="historyDialogOpen = false">Закрыть</v-btn></v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
