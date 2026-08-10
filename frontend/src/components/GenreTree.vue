<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { GenreNodeDto } from '@/types'

defineProps<{ nodes: GenreNodeDto[] }>()
const router = useRouter()
</script>

<template>
  <v-list density="compact" open-strategy="multiple">
    <template v-for="node in nodes" :key="node.id">
      <v-list-group v-if="node.children && node.children.length">
        <template #activator="{ props }">
          <v-list-item
            :title="`${node.title} (${node.bookCount})`"
            @click="router.push({ name: 'books', query: { genre_id: String(node.id) } })"
          >
            <template #append>
              <v-btn
                v-bind="props"
                icon="mdi-chevron-down"
                variant="text"
                size="small"
                aria-label="Раскрыть поджанры"
                @click.stop
              />
            </template>
          </v-list-item>
        </template>
        <GenreTree :nodes="node.children" />
      </v-list-group>
      <v-list-item
        v-else
        :title="`${node.title} (${node.bookCount})`"
        @click="router.push({ name: 'books', query: { genre_id: String(node.id) } })"
      />
    </template>
  </v-list>
</template>
