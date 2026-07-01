<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listsApi } from '@/api/lists'
import ShareQrDialog from '@/components/ShareQrDialog.vue'
import type { BookListDto, ShareLinkDto } from '@/types'

const route = useRoute()
const router = useRouter()

const list = ref<BookListDto | null>(null)
const loading = ref(false)
const shareOpen = ref(false)
const share = ref<ShareLinkDto | null>(null)

async function load(id: number) {
  loading.value = true
  try {
    const { data } = await listsApi.get(id)
    list.value = data
  } finally {
    loading.value = false
  }
}

async function move(index: number, dir: -1 | 1) {
  if (!list.value) return
  const items = [...list.value.items]
  const target = index + dir
  if (target < 0 || target >= items.length) return
  const tmp = items[index]
  items[index] = items[target]
  items[target] = tmp
  const order = items.map((i) => i.bookId)
  const { data } = await listsApi.reorder(list.value.id, order)
  list.value = data
}

async function removeItem(bookId: number) {
  if (!list.value) return
  const { data } = await listsApi.removeItem(list.value.id, bookId)
  list.value = data
}

async function openShare() {
  if (!list.value) return
  const { data } = await listsApi.share(list.value.id)
  share.value = data
  shareOpen.value = true
}

watch(() => route.params.id, (id) => load(Number(id)), { immediate: true })
</script>

<template>
  <div v-if="list">
    <div class="d-flex align-center mb-3">
      <v-btn variant="text" prepend-icon="mdi-arrow-left" @click="router.back()">Назад</v-btn>
      <h1 class="text-h5 ml-2">{{ list.title }}</h1>
      <v-spacer />
      <v-btn color="primary" prepend-icon="mdi-share-variant" @click="openShare">Поделиться</v-btn>
    </div>
    <p v-if="list.description" class="mb-3">{{ list.description }}</p>

    <v-list>
      <v-list-item
        v-for="(item, index) in list.items"
        :key="item.bookId"
        :title="item.title"
        :subtitle="item.authors.map((a) => a.fullName).join(', ')"
        @click="router.push(`/books/${item.bookId}`)"
      >
        <template #append>
          <v-btn icon="mdi-arrow-up" variant="text" @click.stop="move(index, -1)" />
          <v-btn icon="mdi-arrow-down" variant="text" @click.stop="move(index, 1)" />
          <v-btn
            icon="mdi-delete"
            variant="text"
            color="error"
            @click.stop="removeItem(item.bookId)"
          />
        </template>
      </v-list-item>
    </v-list>
    <v-alert v-if="!list.items.length" type="info" variant="tonal">Список пуст</v-alert>

    <ShareQrDialog v-model="shareOpen" :share="share" />
  </div>
</template>
