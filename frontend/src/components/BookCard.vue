<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { BookCardDto } from '@/types'

const MAX_AUTHORS = 2

const props = defineProps<{
  book: BookCardDto
  returnTo?: string
}>()
const router = useRouter()

function openBook() {
  router.push({
    name: 'book-details',
    params: { id: props.book.id },
    query: props.returnTo ? { returnTo: props.returnTo } : undefined,
  })
}

const visibleAuthors = computed(() => props.book.authors.slice(0, MAX_AUTHORS))
const hiddenAuthorsCount = computed(() => props.book.authors.length - visibleAuthors.value.length)
</script>

<template>
  <v-card class="d-flex flex-column h-100" @click="openBook">
    <v-img
      v-if="book.coverUrl"
      :src="book.coverUrl"
      aspect-ratio="2 / 3"
      cover
    >
      <template #placeholder>
        <div class="book-card__cover-fallback">
          <v-icon icon="mdi-book-open-page-variant" size="48" />
        </div>
      </template>
      <template #error>
        <div class="book-card__cover-fallback">
          <v-icon icon="mdi-book-open-page-variant" size="48" />
        </div>
      </template>
    </v-img>
    <div v-else class="book-card__cover-fallback book-card__cover-fallback--portrait">
      <v-icon icon="mdi-book-open-page-variant" size="48" />
    </div>
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
        @click.stop="openBook"
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
.book-card__cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  background-color: rgba(var(--v-theme-on-surface), 0.06);
  color: rgba(var(--v-theme-on-surface), 0.38);
}
.book-card__cover-fallback--portrait {
  aspect-ratio: 2 / 3;
}
</style>
