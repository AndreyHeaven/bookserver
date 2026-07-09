<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { BookCardDto } from '@/types'

const MAX_AUTHORS = 2

const props = defineProps<{ book: BookCardDto }>()
const router = useRouter()

const visibleAuthors = computed(() => props.book.authors.slice(0, MAX_AUTHORS))
const hiddenAuthorsCount = computed(() => props.book.authors.length - visibleAuthors.value.length)
</script>

<template>
  <v-card class="d-flex flex-column h-100" @click="router.push(`/books/${book.id}`)">
    <v-img
      v-if="book.coverUrl"
      :src="book.coverUrl"
      height="180"
      cover
    />
    <v-card-title class="text-body-1 text-wrap">{{ book.title }}</v-card-title>
    <div v-if="book.authors.length" class="px-4 pb-2 d-flex flex-wrap ga-1">
      <v-chip
        v-for="a in visibleAuthors"
        :key="a.id"
        size="small"
        variant="tonal"
        @click.stop="router.push(`/authors/${a.id}`)"
      >
        {{ a.fullName }}
      </v-chip>
      <v-chip
        v-if="hiddenAuthorsCount > 0"
        size="small"
        variant="tonal"
        :title="book.authors.slice(MAX_AUTHORS).map((a) => a.fullName).join(', ')"
        @click.stop="router.push(`/books/${book.id}`)"
      >
        +{{ hiddenAuthorsCount }}
      </v-chip>
    </div>
    <footer class="book-card__footer mt-auto">
      <v-divider />
      <div class="d-flex align-center flex-wrap ga-2 px-4 py-2">
        <v-chip v-if="book.year" size="x-small" variant="tonal">{{ book.year }}</v-chip>
        <v-chip v-if="book.lang" size="x-small" variant="tonal">{{ book.lang }}</v-chip>
        <v-chip v-if="book.fileType" size="x-small" variant="tonal">{{ book.fileType }}</v-chip>
        <v-icon v-if="book.hasFiles" icon="mdi-file-download" size="small" title="Есть файлы" />
      </div>
    </footer>
  </v-card>
</template>

<style scoped>
.book-card__footer {
  background-color: rgba(var(--v-theme-on-surface), 0.02);
}
</style>
