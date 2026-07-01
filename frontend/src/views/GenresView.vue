<script setup lang="ts">
import { ref } from 'vue'
import { genresApi } from '@/api/genres'
import GenreTree from '@/components/GenreTree.vue'
import type { GenreNodeDto } from '@/types'

const nodes = ref<GenreNodeDto[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const { data } = await genresApi.tree()
    nodes.value = data
  } finally {
    loading.value = false
  }
}

load()
</script>

<template>
  <h1 class="text-h5 mb-3">Жанры</h1>
  <v-progress-linear v-if="loading" indeterminate />
  <v-card>
    <GenreTree :nodes="nodes" />
  </v-card>
</template>
