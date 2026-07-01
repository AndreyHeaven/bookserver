<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { booksApi } from '@/api/books'
import BookCard from '@/components/BookCard.vue'
import BookFacets from '@/components/BookFacets.vue'
import type { BookCardDto, BookSearchQuery, FacetCountsDto } from '@/types'

const q = ref('')
const lang = ref<string | null>(null)
const yearFrom = ref<number | null>(null)
const yearTo = ref<number | null>(null)
const genreId = ref<number | null>(null)
const page = ref(1)
const size = ref(24)

const books = ref<BookCardDto[]>([])
const facets = ref<FacetCountsDto | null>(null)
const totalPages = ref(1)
const loading = ref(false)

let debounceTimer: ReturnType<typeof setTimeout> | undefined

function buildQuery(): BookSearchQuery {
  return {
    q: q.value || undefined,
    lang: lang.value || undefined,
    year_from: yearFrom.value ?? undefined,
    year_to: yearTo.value ?? undefined,
    genre_id: genreId.value != null ? [genreId.value] : undefined,
    page: page.value - 1,
    size: size.value,
  }
}

async function load() {
  loading.value = true
  try {
    const { data } = await booksApi.search(buildQuery())
    books.value = data.content
    facets.value = data.facets
    totalPages.value = data.totalPages || 1
  } finally {
    loading.value = false
  }
}

function debouncedReset() {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    page.value = 1
    load()
  }, 350)
}

watch(q, debouncedReset)
watch([lang, yearFrom, yearTo, genreId], () => {
  page.value = 1
  load()
})
watch(page, load)

load()

const hasResults = computed(() => books.value.length > 0)
</script>

<template>
  <v-row>
    <v-col cols="12" md="3">
      <BookFacets
        :facets="facets"
        :lang="lang"
        :year-from="yearFrom"
        :year-to="yearTo"
        :genre-id="genreId"
        @update:lang="lang = $event"
        @update:year-from="yearFrom = $event"
        @update:year-to="yearTo = $event"
        @update:genre-id="genreId = $event"
      />
    </v-col>
    <v-col cols="12" md="9">
      <v-text-field
        v-model="q"
        label="Поиск книг"
        prepend-inner-icon="mdi-magnify"
        clearable
        density="comfortable"
      />
      <v-progress-linear v-if="loading" indeterminate class="mb-2" />
      <v-row v-if="hasResults">
        <v-col v-for="book in books" :key="book.id" cols="12" sm="6" md="4" lg="3">
          <BookCard :book="book" />
        </v-col>
      </v-row>
      <v-alert v-else type="info" variant="tonal">Ничего не найдено</v-alert>
      <v-pagination
        v-if="totalPages > 1"
        v-model="page"
        :length="totalPages"
        class="mt-4"
      />
    </v-col>
  </v-row>
</template>
