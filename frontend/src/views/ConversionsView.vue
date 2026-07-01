<script setup lang="ts">
import { ref } from 'vue'
import { conversionsApi } from '@/api/conversions'
import type { ConversionJobDto } from '@/types'

const jobs = ref<ConversionJobDto[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const { data } = await conversionsApi.list(0, 50)
    jobs.value = data.content
  } finally {
    loading.value = false
  }
}

function statusColor(status: string): string {
  switch (status) {
    case 'SUCCEEDED':
      return 'success'
    case 'FAILED':
    case 'CANCELLED':
      return 'error'
    case 'RUNNING':
      return 'info'
    default:
      return 'warning'
  }
}

load()
</script>

<template>
  <h1 class="text-h5 mb-3">Конвертация</h1>
  <v-alert type="info" variant="tonal" class="mb-3">
    Реальные конвертеры ещё не подключены — задачи завершаются со статусом FAILED.
  </v-alert>
  <v-progress-linear v-if="loading" indeterminate class="mb-2" />
  <v-table>
    <thead>
      <tr>
        <th>ID</th>
        <th>Файл</th>
        <th>Исходный</th>
        <th>Целевой</th>
        <th>Статус</th>
        <th>Сообщение</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="j in jobs" :key="j.id">
        <td>{{ j.id }}</td>
        <td>{{ j.bookFileId }}</td>
        <td>{{ j.sourceFormat }}</td>
        <td>{{ j.targetFormat }}</td>
        <td><v-chip size="small" :color="statusColor(j.status)">{{ j.status }}</v-chip></td>
        <td>{{ j.message }}</td>
      </tr>
    </tbody>
  </v-table>
</template>
