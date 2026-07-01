<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { genresApi } from '@/api/genres'

const props = defineProps<{ genreId: number }>()
const router = useRouter()

interface Crumb {
  id: number
  title: string
}

const crumbs = ref<Crumb[]>([])

async function build(id: number) {
  const chain: Crumb[] = []
  let currentId: number | null = id
  let guard = 0
  while (currentId != null && guard < 50) {
    guard++
    const { data } = await genresApi.get(currentId)
    chain.unshift({ id: data.id, title: data.title })
    currentId = data.parentId
  }
  crumbs.value = chain
}

watch(() => props.genreId, build, { immediate: true })
</script>

<template>
  <v-breadcrumbs :items="[]" class="pa-0">
    <template #default>
      <template v-for="(c, i) in crumbs" :key="c.id">
        <v-breadcrumbs-item
          :disabled="i === crumbs.length - 1"
          @click="router.push(`/genres/${c.id}`)"
        >
          {{ c.title }}
        </v-breadcrumbs-item>
        <v-breadcrumbs-divider v-if="i < crumbs.length - 1" />
      </template>
    </template>
  </v-breadcrumbs>
</template>
