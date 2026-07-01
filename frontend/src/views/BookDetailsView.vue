<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { booksApi } from '@/api/books'
import { listsApi } from '@/api/lists'
import ConvertBookDialog from '@/components/ConvertBookDialog.vue'
import type { BookDetailsDto, BookListDto } from '@/types'

const route = useRoute()
const router = useRouter()

const book = ref<BookDetailsDto | null>(null)
const loading = ref(false)
const convertOpen = ref(false)
const convertFileId = ref<number | null>(null)

const addListOpen = ref(false)
const myLists = ref<BookListDto[]>([])
const selectedListId = ref<number | null>(null)
const addMsg = ref<string | null>(null)

async function load(id: number) {
  loading.value = true
  try {
    const { data } = await booksApi.get(id)
    book.value = data
  } finally {
    loading.value = false
  }
}

function openConvert(fileId: number) {
  convertFileId.value = fileId
  convertOpen.value = true
}

async function openAddToList() {
  addListOpen.value = true
  addMsg.value = null
  const { data } = await listsApi.list(0, 100)
  myLists.value = data.content
}

async function addToList() {
  if (selectedListId.value == null || !book.value) return
  await listsApi.addItem(selectedListId.value, book.value.id)
  addMsg.value = 'Книга добавлена в список'
}

watch(() => route.params.id, (id) => load(Number(id)), { immediate: true })
</script>

<template>
  <div v-if="book">
    <v-btn variant="text" prepend-icon="mdi-arrow-left" @click="router.back()">Назад</v-btn>
    <h1 class="text-h5 my-2">{{ book.title }}</h1>

    <div class="mb-2">
      <strong>Авторы:</strong>
      <v-chip
        v-for="a in book.authors"
        :key="a.id"
        size="small"
        class="ml-1"
        @click="router.push(`/authors/${a.id}`)"
      >
        {{ a.fullName }}
      </v-chip>
    </div>

    <div v-if="book.translators.length" class="mb-2">
      <strong>Переводчики:</strong>
      <span v-for="t in book.translators" :key="t.id" class="ml-1">{{ t.fullName }}</span>
    </div>

    <div class="mb-2">
      <strong>Жанры:</strong>
      <v-chip
        v-for="g in book.genres"
        :key="g.id"
        size="small"
        class="ml-1"
        :title="g.path.join(' / ')"
        @click="router.push(`/genres/${g.id}`)"
      >
        {{ g.title }}
      </v-chip>
    </div>

    <div class="mb-2 text-caption">
      <span v-if="book.year">Год: {{ book.year }} · </span>
      <span v-if="book.lang">Язык: {{ book.lang }} · </span>
      <span v-if="book.fileType">Формат: {{ book.fileType }}</span>
    </div>

    <div v-if="book.series.length" class="mb-2">
      <strong>Серии:</strong>
      <span v-for="s in book.series" :key="s.id" class="ml-1">
        {{ s.title }}<span v-if="s.sequenceNumber"> #{{ s.sequenceNumber }}</span>
      </span>
    </div>

    <v-card v-if="book.annotation" class="my-3">
      <v-card-title class="text-subtitle-1">Аннотация</v-card-title>
      <v-card-text>{{ book.annotation }}</v-card-text>
    </v-card>

    <v-btn color="primary" prepend-icon="mdi-playlist-plus" class="mb-3" @click="openAddToList">
      Добавить в список
    </v-btn>

    <v-card>
      <v-card-title class="text-subtitle-1">Файлы</v-card-title>
      <v-list>
        <v-list-item v-for="f in book.files" :key="f.id" :title="f.format">
          <template #append>
            <v-btn
              icon="mdi-download"
              variant="text"
              :href="f.downloadUrl"
              target="_blank"
              title="Скачать"
            />
            <v-btn
              icon="mdi-swap-horizontal"
              variant="text"
              title="Конвертировать"
              @click="openConvert(f.id)"
            />
          </template>
        </v-list-item>
      </v-list>
    </v-card>

    <ConvertBookDialog v-model="convertOpen" :book-file-id="convertFileId" />

    <v-dialog v-model="addListOpen" max-width="420">
      <v-card>
        <v-card-title>Добавить в список</v-card-title>
        <v-card-text>
          <v-select
            v-model="selectedListId"
            :items="myLists"
            item-title="title"
            item-value="id"
            label="Список"
          />
          <v-alert v-if="addMsg" type="success" variant="tonal">{{ addMsg }}</v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="addListOpen = false">Закрыть</v-btn>
          <v-btn color="primary" @click="addToList">Добавить</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
  <v-progress-linear v-else-if="loading" indeterminate />
</template>
