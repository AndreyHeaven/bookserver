<script setup lang="ts">
import { ref, watch } from 'vue'
import { authorsApi } from '@/api/authors'
import AuthorCard from '@/components/AuthorCard.vue'
import AlphabetNav from '@/components/AlphabetNav.vue'
import type { AlphabetLetterDto, AuthorCardDto } from '@/types'

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
    totalPages.value = data.totalPages || 1
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
</script>

<template>
  <h1 class="text-h5 mb-3">Авторы</h1>
  <v-text-field
    v-model="q"
    label="Поиск авторов"
    prepend-inner-icon="mdi-magnify"
    clearable
    density="comfortable"
  />
  <AlphabetNav :letters="letters" :selected="letter" class="mb-4" @select="letter = $event" />
  <v-progress-linear v-if="loading" indeterminate class="mb-2" />
  <v-row>
    <v-col v-for="a in authors" :key="a.id" cols="12" sm="6" md="4" lg="3">
      <AuthorCard :author="a" />
    </v-col>
  </v-row>
  <v-alert v-if="!authors.length && !loading" type="info" variant="tonal">
    Ничего не найдено
  </v-alert>
  <v-pagination v-if="totalPages > 1" v-model="page" :length="totalPages" class="mt-4" />
</template>
