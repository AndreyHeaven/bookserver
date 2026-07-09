<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { authorsApi } from '@/api/authors'
import AuthorCard from '@/components/AuthorCard.vue'
import AlphabetNav from '@/components/AlphabetNav.vue'
import { usePreferencesStore, type ViewMode } from '@/stores/preferences'
import type { AlphabetLetterDto, AuthorCardDto } from '@/types'

const router = useRouter()
const preferences = usePreferencesStore()

const viewMode = computed<ViewMode>({
  get: () => preferences.authorsViewMode,
  set: (mode) => preferences.setAuthorsViewMode(mode),
})

const tableHeaders = [
  { title: 'ФИО', key: 'fullName', sortable: false },
  { title: 'Книг', key: 'bookCount', sortable: false, align: 'end' as const },
]

const q = ref('')
const letter = ref<string | null>(null)
const page = ref(1)
const size = ref(24)

const authors = ref<AuthorCardDto[]>([])
const letters = ref<AlphabetLetterDto[]>([])
const totalPages = ref(1)
const loading = ref(false)

let debounceTimer: ReturnType<typeof setTimeout> | undefined

async function load() {
  loading.value = true
  try {
    const { data } = await authorsApi.list({
      q: q.value || undefined,
      letter: letter.value || undefined,
      page: page.value - 1,
      size: size.value,
    })
    authors.value = data.content
    totalPages.value = data.page.totalPages || 1
  } finally {
    loading.value = false
  }
}

async function loadAlphabet() {
  const { data } = await authorsApi.alphabet()
  letters.value = data
}

watch(q, () => {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    page.value = 1
    load()
  }, 350)
})
watch(letter, () => {
  page.value = 1
  load()
})
watch(page, load)

loadAlphabet()
load()

function openAuthor(author: AuthorCardDto) {
  router.push(`/authors/${author.id}`)
}

function onRowClick(_event: unknown, row: { item: AuthorCardDto }) {
  openAuthor(row.item)
}
</script>

<template>
  <div class="d-flex align-center justify-space-between ga-2 mb-3">
    <h1 class="text-h5">Авторы</h1>
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
  <v-text-field
    v-model="q"
    label="Поиск авторов"
    prepend-inner-icon="mdi-magnify"
    clearable
    density="comfortable"
  />
  <AlphabetNav :letters="letters" :selected="letter" class="mb-4" @select="letter = $event" />
  <v-progress-linear v-if="loading" indeterminate class="mb-2" />
  <v-row v-if="viewMode === 'cards'">
    <v-col v-for="a in authors" :key="a.id" cols="12" sm="6" md="4" lg="3">
      <AuthorCard :author="a" />
    </v-col>
  </v-row>
  <v-data-table
    v-else-if="authors.length"
    :headers="tableHeaders"
    :items="authors"
    item-value="id"
    hide-default-footer
    :items-per-page="-1"
    hover
    @click:row="onRowClick"
  >
    <template #[`item.bookCount`]="{ item }">
      {{ item.bookCount }}
    </template>
  </v-data-table>
  <v-alert v-if="!authors.length && !loading" type="info" variant="tonal">
    Ничего не найдено
  </v-alert>
  <v-pagination v-if="totalPages > 1" v-model="page" :length="totalPages" class="mt-4" />
</template>

<style scoped>
:deep(.v-data-table__tr) {
  cursor: pointer;
}
</style>
