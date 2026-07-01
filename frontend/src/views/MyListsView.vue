<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { listsApi } from '@/api/lists'
import type { BookListDto } from '@/types'

const router = useRouter()

const lists = ref<BookListDto[]>([])
const loading = ref(false)
const createOpen = ref(false)
const newTitle = ref('')
const newDescription = ref('')

async function load() {
  loading.value = true
  try {
    const { data } = await listsApi.list(0, 100)
    lists.value = data.content
  } finally {
    loading.value = false
  }
}

async function create() {
  if (!newTitle.value) return
  await listsApi.create({ title: newTitle.value, description: newDescription.value || undefined })
  newTitle.value = ''
  newDescription.value = ''
  createOpen.value = false
  load()
}

async function remove(id: number) {
  await listsApi.remove(id)
  load()
}

load()
</script>

<template>
  <div class="d-flex align-center mb-3">
    <h1 class="text-h5">Мои списки</h1>
    <v-spacer />
    <v-btn color="primary" prepend-icon="mdi-plus" @click="createOpen = true">Создать</v-btn>
  </div>

  <v-progress-linear v-if="loading" indeterminate class="mb-2" />
  <v-list>
    <v-list-item
      v-for="l in lists"
      :key="l.id"
      :title="l.title"
      :subtitle="l.description ?? ''"
      @click="router.push(`/lists/${l.id}`)"
    >
      <template #append>
        <v-btn
          icon="mdi-delete"
          variant="text"
          color="error"
          @click.stop="remove(l.id)"
        />
      </template>
    </v-list-item>
  </v-list>
  <v-alert v-if="!lists.length && !loading" type="info" variant="tonal">Списков пока нет</v-alert>

  <v-dialog v-model="createOpen" max-width="420">
    <v-card>
      <v-card-title>Новый список</v-card-title>
      <v-card-text>
        <v-text-field v-model="newTitle" label="Название" />
        <v-textarea v-model="newDescription" label="Описание" rows="2" />
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn @click="createOpen = false">Отмена</v-btn>
        <v-btn color="primary" @click="create">Создать</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
