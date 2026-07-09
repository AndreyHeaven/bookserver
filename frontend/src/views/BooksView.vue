<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { booksApi } from '@/api/books'
import BookCard from '@/components/BookCard.vue'
import BookFacets from '@/components/BookFacets.vue'
import { usePreferencesStore, type ViewMode } from '@/stores/preferences'
import type { BookCardDto, BookSearchQuery, FacetCountsDto } from '@/types'

const router = useRouter()
const preferences = usePreferencesStore()

const viewMode = computed<ViewMode>({
  get: () => preferences.booksViewMode,
  set: (mode) => preferences.setBooksViewMode(mode),
})

const tableHeaders = [
  { title: '', key: 'coverUrl', sortable: false, width: 56 },
  { title: 'Название', key: 'title', sortable: false },
  { title: 'Авторы', key: 'authors', sortable: false },
  { title: 'Год', key: 'year', sortable: false },
  { title: 'Язык', key: 'lang', sortable: false },
  { title: 'Формат', key: 'fileType', sortable: false },
  { title: 'Файлы', key: 'hasFiles', sortable: false, align: 'center' as const },
]

const q = ref('')
const lang = ref<string[]>([])
const yearFrom = ref<number | null>(null)
const yearTo = ref<number | null>(null)
const genreIds = ref<number[]>([])
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
    lang: lang.value.length ? lang.value : undefined,
    year_from: yearFrom.value ?? undefined,
    year_to: yearTo.value ?? undefined,
    genre_id: genreIds.value.length ? genreIds.value : undefined,
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
watch([lang, yearFrom, yearTo, genreIds], () => {
  page.value = 1
  load()
})
watch(page, load)

load()

const hasResults = computed(() => books.value.length > 0)

function authorsText(book: BookCardDto): string {
  return book.authors.map((a) => a.fullName).join(', ')
}

function openBook(book: BookCardDto) {
  router.push(`/books/${book.id}`)
}

function onRowClick(_event: unknown, row: { item: BookCardDto }) {
  openBook(row.item)
}
</script>

<template>
  <v-row>
    <v-col cols="12" md="3">
      <BookFacets
        :facets="facets"
        :lang="lang"
        :year-from="yearFrom"
        :year-to="yearTo"
        :genre-ids="genreIds"
        @update:lang="lang = $event"
        @update:year-from="yearFrom = $event"
        @update:year-to="yearTo = $event"
        @update:genre-ids="genreIds = $event"
      />
    </v-col>
    <v-col cols="12" md="9">
      <div class="d-flex align-center ga-2">
        <v-text-field
          v-model="q"
          label="Поиск книг"
          prepend-inner-icon="mdi-magnify"
          clearable
          density="comfortable"
          hide-details
          class="flex-grow-1"
        />
        <v-btn-toggle
          v-model="viewMode"
          mandatory
          density="comfortable"
          color="primary"
          variant="outlined"
        >
          <v-btn value="cards" icon="mdi-view-grid" title="Карточки" aria-label="Карточки" />
          <v-btn value="table" icon="mdi-table" title="Таблица" aria-label="Таблица" />
        </v-btn-toggle>
      </div>
      <v-progress-linear v-if="loading" indeterminate class="mt-2 mb-2" />
      <template v-if="hasResults">
        <v-row v-if="viewMode === 'cards'" class="mt-1">
          <v-col v-for="book in books" :key="book.id" cols="12" sm="6" md="4" lg="3">
            <BookCard :book="book" />
          </v-col>
        </v-row>
        <v-data-table
          v-else
          :headers="tableHeaders"
          :items="books"
          item-value="id"
          hide-default-footer
          :items-per-page="-1"
          class="mt-2"
          hover
          @click:row="onRowClick"
        >
          <template #[`item.coverUrl`]="{ item }">
            <v-img
              v-if="item.coverUrl"
              :src="item.coverUrl"
              width="40"
              height="56"
              cover
              class="my-1"
            >
              <template #error>
                <div class="books-table__cover-fallback">
                  <v-icon icon="mdi-book-open-page-variant" size="20" />
                </div>
              </template>
            </v-img>
            <div v-else class="books-table__cover-fallback">
              <v-icon icon="mdi-book-open-page-variant" size="20" />
            </div>
          </template>
          <template #[`item.authors`]="{ item }">
            {{ authorsText(item) }}
          </template>
          <template #[`item.year`]="{ item }">
            {{ item.year ?? '—' }}
          </template>
          <template #[`item.hasFiles`]="{ item }">
            <v-icon
              v-if="item.hasFiles"
              icon="mdi-file-download"
              size="small"
              title="Есть файлы"
            />
            <span v-else>—</span>
          </template>
        </v-data-table>
      </template>
      <v-alert v-else type="info" variant="tonal" class="mt-2">Ничего не найдено</v-alert>
      <v-pagination
        v-if="totalPages > 1"
        v-model="page"
        :length="totalPages"
        class="mt-4"
      />
    </v-col>
  </v-row>
</template>

<style scoped>
.books-table__cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 56px;
  background-color: rgba(var(--v-theme-on-surface), 0.06);
  color: rgba(var(--v-theme-on-surface), 0.38);
}
.books-table__cover-fallback + span,
:deep(.v-data-table__tr) {
  cursor: pointer;
}
</style>
