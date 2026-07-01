<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { genresApi } from '@/api/genres'
import BookCard from '@/components/BookCard.vue'
import GenreBreadcrumbs from '@/components/GenreBreadcrumbs.vue'
import type { BookCardDto, GenreDetailsDto } from '@/types'

const route = useRoute()
const router = useRouter()

const genre = ref<GenreDetailsDto | null>(null)
const books = ref<BookCardDto[]>([])
const includeSubgenres = ref(true)
const page = ref(1)
const size = ref(24)
const totalPages = ref(1)
const loading = ref(false)

async function loadGenre(id: number) {
  const { data } = await genresApi.get(id)
  genre.value = data
}

async function loadBooks() {
  const id = Number(route.params.id)
  loading.value = true
  try {
    const { data } = await genresApi.books(id, {
      includeSubgenres: includeSubgenres.value,
      page: page.value - 1,
      size: size.value,
    })
    books.value = data.content
    totalPages.value = data.totalPages || 1
  } finally {
    loading.value = false
  }
}

watch(
  () => route.params.id,
  (id) => {
    page.value = 1
    loadGenre(Number(id))
    loadBooks()
  },
  { immediate: true },
)
watch(includeSubgenres, () => {
  page.value = 1
  loadBooks()
})
watch(page, loadBooks)
</script>

<template>
  <div v-if="genre">
    <GenreBreadcrumbs :genre-id="genre.id" />
    <h1 class="text-h5 mb-1">{{ genre.title }}</h1>
    <div class="text-caption mb-3">Книг: {{ genre.bookCount }}</div>

    <div v-if="genre.children.length" class="mb-3">
      <v-chip
        v-for="c in genre.children"
        :key="c.id"
        size="small"
        class="mr-1 mb-1"
        @click="router.push(`/genres/${c.id}`)"
      >
        {{ c.title }} ({{ c.bookCount }})
      </v-chip>
    </div>

    <v-switch
      v-model="includeSubgenres"
      label="включать поджанры"
      color="primary"
      hide-details
      class="mb-2"
    />

    <v-progress-linear v-if="loading" indeterminate class="mb-2" />
    <v-row>
      <v-col v-for="book in books" :key="book.id" cols="12" sm="6" md="4" lg="3">
        <BookCard :book="book" />
      </v-col>
    </v-row>
    <v-pagination v-if="totalPages > 1" v-model="page" :length="totalPages" class="mt-4" />
  </div>
</template>
