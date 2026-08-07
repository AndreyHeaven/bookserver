<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { booksApi } from '@/api/books'
import BookCard from '@/components/BookCard.vue'
import BookFacets from '@/components/BookFacets.vue'
import { usePreferencesStore, type ViewMode } from '@/stores/preferences'
import type { BookCardDto, BookSearchQuery, FacetCountsDto } from '@/types'
import type { GenreOption } from '@/utils/genres'

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
const totalElements = ref(0)
const loading = ref(false)
const searchingRandomBook = ref(false)
const filtersOpen = ref(false)
const genreOptions = ref<GenreOption[]>([])

const appliedFilters = ref<Omit<BookSearchQuery, 'page' | 'size'>>({})

function buildQuery(): BookSearchQuery {
  return {
    ...appliedFilters.value,
    page: page.value - 1,
    size: size.value,
  }
}

function saveAppliedFilters() {
  appliedFilters.value = {
    q: q.value || undefined,
    lang: lang.value.length ? [...lang.value] : undefined,
    year_from: yearFrom.value ?? undefined,
    year_to: yearTo.value ?? undefined,
    genre_id: genreIds.value.length ? [...genreIds.value] : undefined,
  }
}

async function load() {
  loading.value = true
  try {
    const { data } = await booksApi.search(buildQuery())
    books.value = data.content
    totalPages.value = data.totalPages || 1
    totalElements.value = data.totalElements
  } finally {
    loading.value = false
  }
}

async function loadFacets() {
  const { data } = await booksApi.facets(appliedFilters.value)
  facets.value = data
}

function search() {
  saveAppliedFilters()
  void loadFacets()
  if (page.value === 1) {
    load()
  } else {
    page.value = 1
  }
}

function resetFilters() {
  q.value = ''
  lang.value = []
  yearFrom.value = null
  yearTo.value = null
  genreIds.value = []
  search()
}

async function openRandomBook() {
  if (totalElements.value === 0) {
    return
  }

  searchingRandomBook.value = true
  try {
    const { data } = await booksApi.search({
      ...appliedFilters.value,
      page: Math.floor(Math.random() * totalElements.value),
      size: 1,
    })
    const book = data.content[0]
    if (book) {
      openBook(book)
    }
  } finally {
    searchingRandomBook.value = false
  }
}

watch(page, load)

void Promise.all([load(), loadFacets()])

const hasResults = computed(() => books.value.length > 0)

const activeFiltersSummary = computed(() => {
  const parts: string[] = []
  if (lang.value.length) parts.push(`Языки: ${lang.value.join(', ')}`)
  if (yearFrom.value != null || yearTo.value != null) {
    const range = yearFrom.value != null && yearTo.value != null
      ? `${yearFrom.value}–${yearTo.value}`
      : yearFrom.value != null ? `с ${yearFrom.value}` : `по ${yearTo.value}`
    parts.push(`Год: ${range}`)
  }
  const genres = genreIds.value
    .map((id) => genreOptions.value.find((genre) => genre.id === id)?.title)
    .filter((title): title is string => title != null)
  if (genres.length) parts.push(`Жанры: ${genres.join(', ')}`)
  return parts.join(' · ')
})

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
  <v-card>
    <v-card-text>
      <v-form @submit.prevent="search">
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
          <v-btn
            :variant="filtersOpen ? 'tonal' : 'outlined'"
            prepend-icon="mdi-filter-variant"
            @click="filtersOpen = !filtersOpen"
          >
            Фильтры
          </v-btn>
          <div v-if="!filtersOpen" class="d-flex align-center ga-2">
            <v-btn color="primary" type="submit" prepend-icon="mdi-magnify">Найти</v-btn>
            <v-btn variant="text" prepend-icon="mdi-filter-remove" @click="resetFilters">Сбросить</v-btn>
            <v-btn
              variant="tonal"
              prepend-icon="mdi-dice-multiple"
              :disabled="totalElements === 0 || searchingRandomBook"
              :loading="searchingRandomBook"
              @click="openRandomBook"
            >
              Мне повезёт
            </v-btn>
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
        </div>
        <div v-if="!filtersOpen && activeFiltersSummary" class="text-caption text-medium-emphasis mt-2">
          {{ activeFiltersSummary }}
        </div>

        <v-expand-transition>
          <div v-if="filtersOpen" class="mt-4">
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
              @loaded:genres="genreOptions = $event"
            />
          </div>
        </v-expand-transition>

        <div v-if="filtersOpen" class="d-flex align-center ga-2 mt-4">
          <v-btn color="primary" type="submit" prepend-icon="mdi-magnify">Найти</v-btn>
          <v-btn variant="text" prepend-icon="mdi-filter-remove" @click="resetFilters">Сбросить</v-btn>
          <v-btn
            variant="tonal"
            prepend-icon="mdi-dice-multiple"
            :disabled="totalElements === 0 || searchingRandomBook"
            :loading="searchingRandomBook"
            @click="openRandomBook"
          >
            Мне повезёт
          </v-btn>
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
      </v-form>
    </v-card-text>
  </v-card>
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
    :total-visible="5"
    class="mt-4"
  />
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
