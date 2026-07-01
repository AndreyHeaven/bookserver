<script setup lang="ts">
import type { ShareLinkDto } from '@/types'

defineProps<{ modelValue: boolean; share: ShareLinkDto | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

async function copy(url: string) {
  try {
    await navigator.clipboard.writeText(url)
  } catch {
    /* ignore */
  }
}
</script>

<template>
  <v-dialog
    :model-value="modelValue"
    max-width="420"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <v-card>
      <v-card-title>Поделиться списком</v-card-title>
      <v-card-text v-if="share">
        <div class="d-flex justify-center mb-4">
          <img
            :src="'data:image/png;base64,' + share.qrPngBase64"
            alt="QR"
            width="200"
            height="200"
          />
        </div>
        <v-text-field
          :model-value="share.publicUrl"
          label="Публичная ссылка"
          readonly
          append-inner-icon="mdi-content-copy"
          @click:append-inner="copy(share.publicUrl)"
        />
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn @click="emit('update:modelValue', false)">Закрыть</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
