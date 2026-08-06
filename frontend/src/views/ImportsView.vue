<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import { importsApi } from '@/api/imports'
import type { ArchiveImportMode, ImporterType, ImportJobDto } from '@/types'

const types: { title: string; value: ImporterType }[] = [
  { title: 'INPX ZIP', value: 'inpx-zip' },
  { title: 'FB2 папка', value: 'fb2-folder' },
]

const type = ref<ImporterType>('inpx-zip')
const sourcePath = ref('')
const stopOnError = ref(true)
const archiveImportMode = ref<ArchiveImportMode>('importAll')
const jobs = ref<ImportJobDto[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

let pollTimer: ReturnType<typeof setInterval> | undefined

async function load() {
  const { data } = await importsApi.list(0, 50)
  jobs.value = data.content
  setupPolling()
}

function setupPolling() {
  const active = jobs.value.some((j) => j.status === 'RUNNING' || j.status === 'PENDING')
  if (active && !pollTimer) {
    pollTimer = setInterval(load, 3000)
  } else if (!active && pollTimer) {
    clearInterval(pollTimer)
    pollTimer = undefined
  }
}

async function submit() {
  if (!sourcePath.value) return
  loading.value = true
  error.value = null
  try {
    await importsApi.create({
      type: type.value,
      sourcePath: sourcePath.value,
      options: {
        stopOnError: stopOnError.value,
        archiveImportMode: archiveImportMode.value,
      },
    })
    sourcePath.value = ''
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Не удалось запустить импорт'
  } finally {
    loading.value = false
  }
}

function statusColor(status: string): string {
  switch (status) {
    case 'SUCCEEDED':
      return 'success'
    case 'FAILED':
      return 'error'
    case 'RUNNING':
      return 'info'
    default:
      return 'warning'
  }
}

function progressPercent(job: ImportJobDto): number {
  if (job.totalCount <= 0) return 0
  return Math.min(100, Math.round((job.processedCount / job.totalCount) * 100))
}

load()
onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<template>
  <h1 class="text-h5 mb-3">Импорт</h1>
  <v-card class="mb-4">
    <v-card-text>
      <v-row dense>
        <v-col cols="12" sm="4">
          <v-select v-model="type" :items="types" label="Тип" />
        </v-col>
        <v-col cols="12" sm="6">
          <v-text-field v-model="sourcePath" label="Путь к источнику" />
        </v-col>
        <v-col cols="12" sm="3" class="d-flex align-center">
          <v-checkbox v-model="stopOnError" label="Остановить при ошибке" hide-details />
        </v-col>
        <v-col cols="12" sm="5">
          <v-radio-group v-model="archiveImportMode" label="Архивы" inline hide-details>
            <v-radio label="Импортировать все" value="importAll" />
            <v-radio label="Пропускать по имени (быстро)" value="skipByName" />
            <v-radio label="Пропускать по хешу (надёжно)" value="skipByHash" />
          </v-radio-group>
        </v-col>
        <v-col cols="12" sm="2" class="d-flex align-center">
          <v-btn color="primary" :loading="loading" block @click="submit">Запустить</v-btn>
        </v-col>
      </v-row>
      <v-alert v-if="error" type="error" variant="tonal">{{ error }}</v-alert>
    </v-card-text>
  </v-card>

  <v-table>
    <thead>
      <tr>
        <th>ID</th>
        <th>Тип</th>
        <th>Путь</th>
        <th>Статус</th>
        <th>Прогресс</th>
        <th>Сообщение</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="j in jobs" :key="j.id">
        <td>{{ j.id }}</td>
        <td>{{ j.importerType }}</td>
        <td>{{ j.sourcePath }}</td>
        <td><v-chip size="small" :color="statusColor(j.status)">{{ j.status }}</v-chip></td>
        <td>{{ j.processedCount }} из {{ j.totalCount }} ({{ progressPercent(j) }}%)</td>
        <td class="job-message">{{ j.message }}</td>
      </tr>
    </tbody>
  </v-table>
</template>

<style scoped>
.job-message {
  white-space: pre-line;
}
</style>
