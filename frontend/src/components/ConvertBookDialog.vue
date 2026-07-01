<script setup lang="ts">
import { ref } from 'vue'
import { conversionsApi } from '@/api/conversions'
import type { ConversionFormat, ConversionJobDto } from '@/types'

const props = defineProps<{ modelValue: boolean; bookFileId: number | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const formats: ConversionFormat[] = ['fb2', 'epub', 'mobi', 'pdf', 'azw3']
const target = ref<ConversionFormat>('epub')
const loading = ref(false)
const job = ref<ConversionJobDto | null>(null)
const error = ref<string | null>(null)

async function submit() {
  if (props.bookFileId == null) return
  loading.value = true
  error.value = null
  job.value = null
  try {
    const { data } = await conversionsApi.create({
      bookFileId: props.bookFileId,
      targetFormat: target.value,
    })
    job.value = data
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Ошибка запуска конвертации'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <v-dialog
    :model-value="modelValue"
    max-width="480"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <v-card>
      <v-card-title>Конвертировать книгу</v-card-title>
      <v-card-text>
        <v-select
          v-model="target"
          :items="formats"
          label="Целевой формат"
        />
        <v-alert type="info" variant="tonal" density="compact" class="mt-2">
          Реальные конвертеры ещё не подключены.
        </v-alert>
        <v-alert v-if="error" type="error" variant="tonal" class="mt-2">{{ error }}</v-alert>
        <v-alert v-if="job" type="warning" variant="tonal" class="mt-2">
          Задача #{{ job.id }}: статус {{ job.status }}.
          <span v-if="job.message"> {{ job.message }}</span>
        </v-alert>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn @click="emit('update:modelValue', false)">Закрыть</v-btn>
        <v-btn color="primary" :loading="loading" @click="submit">Конвертировать</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
