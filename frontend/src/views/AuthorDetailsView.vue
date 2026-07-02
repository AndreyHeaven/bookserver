<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { authorsApi } from '@/api/authors'
import BookCard from '@/components/BookCard.vue'
import type { AuthorDetailsDto, BookCardDto } from '@/types'

const route = useRoute()

const author = ref<AuthorDetailsDto | null>(null)
const books = ref<BookCardDto[]>([])
const lang = ref<string | null>(null)
const yearFrom = ref<number | null>(null)
const yearTo = ref<number | null>(null)
const genreId = ref<number | null>(null)
const page = ref(1)
const size = ref(24)
const totalPages = ref(1)
const loading = ref(false)

async function loadAuthor(id: number) {
  const { data } = await authorsApi.get(id)
  author.value = data
}

async function loadBooks() {
  const id = Number(route.params.id)
  loading.value = true
  try {
    const { data } = await authorsApi.books(id, {
      lang: lang.value || undefined,
      year_from: yearFrom.value ?? undefined,
      year_to: yearTo.value ?? undefined,
      genre_id: genreId.value != null ? [genreId.value] : undefined,
      page: page.value - 1,
      size: size.value,
    })
    books.value = data.content
    totalPages.value = data.page.totalPages || 1
  } finally {
    loading.value = false
  }
}

watch(
  () => route.params.id,
  (id) => {
    page.value = 1
    loadAuthor(Number(id))
    loadBooks()
  },
  { immediate: true },
)
watch([lang, yearFrom, yearTo, genreId], () => {
  page.value = 1
  loadBooks()
})
watch(page, loadBooks)
</script>

<template>
  <div v-if="author">
    <h1 class="text-h5">{{ author.fullName }}</h1>
    <div class="text-caption mb-4">Книг: {{ author.bookCount }}</div>

    <v-row class="mb-2" dense>
      <v-col cols="6" sm="3">
        <v-text-field v-model="lang" label="Язык" density="compact" hide-details clearable />
      </v-col>
      <v-col cols="6" sm="3">
        <v-text-field
          v-model.number="yearFrom"
          label="Год с"
          type="number"
          density="compact"
          hide-details
        />
      </v-col>
      <v-col cols="6" sm="3">
        <v-text-field
          v-model.number="yearTo"
          label="Год по"
          type="number"
          density="compact"
          hide-details
        />
      </v-col>
      <v-col cols="6" sm="3">
        <v-text-field
          v-model.number="genreId"
          label="ID жанра"
          type="number"
          density="compact"
          hide-details
        />
      </v-col>
    </v-row>

    <v-progress-linear v-if="loading" indeterminate class="mb-2" />
    <v-row>
      <v-col v-for="book in books" :key="book.id" cols="12" sm="6" md="4" lg="3">
        <BookCard :book="book" />
      </v-col>
    </v-row>
    <v-pagination v-if="totalPages > 1" v-model="page" :length="totalPages" class="mt-4" />
  </div>
</template>
