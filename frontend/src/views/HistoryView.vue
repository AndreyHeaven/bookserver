<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { historyApi, type HistoryEntry } from '@/api/history'
const items = ref<HistoryEntry[]>([]); const page = ref(1); const totalPages = ref(1); const loading = ref(false)
async function load() { loading.value = true; try { const { data } = await historyApi.list(page.value - 1); items.value = data.content; totalPages.value = Math.max(data.page.totalPages, 1) } finally { loading.value = false } }
async function remove(id: number) { await historyApi.remove(id); await load() }
async function clear() { if (confirm('Очистить историю просмотров?')) { await historyApi.clear(); await load() } }
watch(page, load); onMounted(load)
</script>
<template><div><div class="d-flex align-center justify-space-between mb-4"><h1 class="text-h5">История просмотров</h1><v-btn color="error" variant="tonal" @click="clear">Очистить</v-btn></div><v-progress-linear v-if="loading" indeterminate /><v-row><v-col v-for="item in items" :key="item.id" cols="12" sm="6" md="4"><v-card :to="`/books/${item.bookId}`"><v-img :src="item.coverUrl" height="220" cover /><v-card-title>{{ item.title }}</v-card-title><v-card-subtitle>{{ new Date(item.viewedAt).toLocaleString() }}</v-card-subtitle><v-card-actions><v-spacer /><v-btn icon="mdi-delete" variant="text" @click.prevent="remove(item.id)" /></v-card-actions></v-card></v-col></v-row><v-pagination v-model="page" :length="totalPages" class="my-3" /></div></template>
