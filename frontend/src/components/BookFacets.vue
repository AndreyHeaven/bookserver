<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { genresApi } from '@/api/genres'
import { flattenGenres, type GenreOption } from '@/utils/genres'
import type { FacetCountsDto } from '@/types'

withDefaults(defineProps<{
  facets: FacetCountsDto | null
  lang: string[]
  yearFrom: number | null
  yearTo: number | null
  genreIds: number[]
}>(), {
  lang: () => [],
})

const emit = defineEmits<{
  'update:lang': [value: string[]]
  'update:yearFrom': [value: number | null]
  'update:yearTo': [value: number | null]
  'update:genreIds': [value: number[]]
}>()

const genreOptions = ref<GenreOption[]>([])

async function loadGenres() {
  const { data } = await genresApi.tree()
  genreOptions.value = flattenGenres(data)
}

onMounted(loadGenres)
</script>

<template>
  <v-card>
    <v-card-title class="text-subtitle-1">Фильтры</v-card-title>
    <v-card-text>
      <div class="text-subtitle-2 mb-1">Языки</div>
      <v-autocomplete
        :model-value="lang"
        :items="facets?.langs ?? []"
        item-title="value"
        item-value="value"
        label="Языки"
        density="compact"
        hide-details
        multiple
        chips
        closable-chips
        clearable
        @update:model-value="emit('update:lang', $event ?? [])"
      >
        <template #item="{ props: itemProps, item }">
          <v-list-item
            v-bind="itemProps"
            :title="`${item.raw.value} (${item.raw.count})`"
          />
        </template>
      </v-autocomplete>

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
      <v-autocomplete
        :model-value="genreIds"
        :items="genreOptions"
        item-title="title"
        item-value="id"
        label="Жанры"
        density="compact"
        hide-details
        multiple
        chips
        closable-chips
        clearable
        @update:model-value="emit('update:genreIds', $event ?? [])"
      >
        <template #item="{ props: itemProps, item }">
          <v-list-item
            v-bind="itemProps"
            :title="item.raw.title"
            :subtitle="item.raw.path || undefined"
          />
        </template>
      </v-autocomplete>
    </v-card-text>
  </v-card>
</template>
