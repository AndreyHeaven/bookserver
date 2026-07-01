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
            v-bind="props"
            :title="`${node.title} (${node.bookCount})`"
            @click.stop="router.push(`/genres/${node.id}`)"
          />
        </template>
        <GenreTree :nodes="node.children" />
      </v-list-group>
      <v-list-item
        v-else
        :title="`${node.title} (${node.bookCount})`"
        @click="router.push(`/genres/${node.id}`)"
      />
    </template>
  </v-list>
</template>
