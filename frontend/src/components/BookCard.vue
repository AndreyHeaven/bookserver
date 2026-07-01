<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { BookCardDto } from '@/types'

defineProps<{ book: BookCardDto }>()
const router = useRouter()
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
    <v-card-subtitle>
      <v-chip
        v-for="a in book.authors"
        :key="a.id"
        size="small"
        class="mr-1 mb-1"
        @click.stop="router.push(`/authors/${a.id}`)"
      >
        {{ a.fullName }}
      </v-chip>
    </v-card-subtitle>
    <v-card-text class="mt-auto">
      <div class="d-flex align-center flex-wrap ga-2">
        <v-chip v-if="book.year" size="x-small" variant="tonal">{{ book.year }}</v-chip>
        <v-chip v-if="book.lang" size="x-small" variant="tonal">{{ book.lang }}</v-chip>
        <v-chip v-if="book.fileType" size="x-small" variant="tonal">{{ book.fileType }}</v-chip>
        <v-icon v-if="book.hasFiles" icon="mdi-file-download" size="small" title="Есть файлы" />
      </div>
    </v-card-text>
  </v-card>
</template>
