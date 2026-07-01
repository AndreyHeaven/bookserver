<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { AuthorCardDto } from '@/types'

defineProps<{ author: AuthorCardDto }>()
const router = useRouter()
</script>

<template>
  <v-card @click="router.push(`/authors/${author.id}`)">
    <v-card-item>
      <template #prepend>
        <v-avatar color="primary">
          <v-icon icon="mdi-account" />
        </v-avatar>
      </template>
      <v-card-title class="text-body-1">{{ author.fullName }}</v-card-title>
      <v-card-subtitle>Книг: {{ author.bookCount }}</v-card-subtitle>
    </v-card-item>
  </v-card>
</template>
