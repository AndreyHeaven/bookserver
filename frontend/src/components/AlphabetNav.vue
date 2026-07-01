<script setup lang="ts">
import type { AlphabetLetterDto } from '@/types'

defineProps<{ letters: AlphabetLetterDto[]; selected: string | null }>()
const emit = defineEmits<{ select: [letter: string | null] }>()
</script>

<template>
  <div class="d-flex flex-wrap ga-1">
    <v-chip
      size="small"
      :variant="selected === null ? 'flat' : 'outlined'"
      color="primary"
      @click="emit('select', null)"
    >
      Все
    </v-chip>
    <v-chip
      v-for="l in letters"
      :key="l.letter"
      size="small"
      :variant="selected === l.letter ? 'flat' : 'outlined'"
      color="primary"
      @click="emit('select', l.letter)"
    >
      {{ l.letter }} ({{ l.count }})
    </v-chip>
  </div>
</template>
