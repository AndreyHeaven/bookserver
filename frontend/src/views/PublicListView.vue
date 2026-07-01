<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { publicApi } from '@/api/public'
import type { PublicBookListDto } from '@/types'

const route = useRoute()

const list = ref<PublicBookListDto | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

async function load(token: string) {
  loading.value = true
  error.value = null
  try {
    const { data } = await publicApi.getList(token)
    list.value = data
  } catch {
    error.value = 'Список не найден или ссылка отозвана'
  } finally {
    loading.value = false
  }
}

watch(() => route.params.token, (t) => load(String(t)), { immediate: true })
</script>

<template>
  <v-container>
    <v-progress-linear v-if="loading" indeterminate />
    <v-alert v-if="error" type="error" variant="tonal">{{ error }}</v-alert>
    <div v-if="list">
      <h1 class="text-h5 mb-1">{{ list.title }}</h1>
      <p v-if="list.description" class="mb-3">{{ list.description }}</p>
      <v-list>
        <v-list-item
          v-for="item in list.items"
          :key="item.bookId"
          :title="item.title"
          :subtitle="item.authors.map((a) => a.fullName).join(', ')"
        >
          <template #append>
            <span v-if="item.year" class="text-caption">{{ item.year }}</span>
          </template>
        </v-list-item>
      </v-list>
      <v-alert v-if="!list.items.length" type="info" variant="tonal">Список пуст</v-alert>
    </div>
  </v-container>
</template>
