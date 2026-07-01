<script setup lang="ts">
import type { FacetCountsDto } from '@/types'

defineProps<{
  facets: FacetCountsDto | null
  lang: string | null
  yearFrom: number | null
  yearTo: number | null
  genreId: number | null
}>()

const emit = defineEmits<{
  'update:lang': [value: string | null]
  'update:yearFrom': [value: number | null]
  'update:yearTo': [value: number | null]
  'update:genreId': [value: number | null]
}>()
</script>

<template>
  <v-card>
    <v-card-title class="text-subtitle-1">Фильтры</v-card-title>
    <v-card-text>
      <div class="text-subtitle-2 mb-1">Язык</div>
      <v-chip-group
        :model-value="lang"
        column
        @update:model-value="emit('update:lang', $event ?? null)"
      >
        <v-chip
          v-for="l in facets?.langs ?? []"
          :key="l.value"
          :value="l.value"
          size="small"
          filter
        >
          {{ l.value }} ({{ l.count }})
        </v-chip>
      </v-chip-group>

      <v-divider class="my-3" />
      <div class="text-subtitle-2 mb-1">Год</div>
      <div class="d-flex ga-2">
        <v-text-field
          :model-value="yearFrom"
          label="с"
          type="number"
          density="compact"
          hide-details
          @update:model-value="emit('update:yearFrom', $event ? Number($event) : null)"
        />
        <v-text-field
          :model-value="yearTo"
          label="по"
          type="number"
          density="compact"
          hide-details
          @update:model-value="emit('update:yearTo', $event ? Number($event) : null)"
        />
      </div>

      <v-divider class="my-3" />
      <div class="text-subtitle-2 mb-1">Жанр</div>
      <v-chip-group
        :model-value="genreId"
        column
        @update:model-value="emit('update:genreId', $event ?? null)"
      >
        <v-chip
          v-for="g in facets?.genres ?? []"
          :key="g.id"
          :value="g.id"
          size="small"
          filter
        >
          {{ g.title }} ({{ g.count }})
        </v-chip>
      </v-chip-group>
    </v-card-text>
  </v-card>
</template>
