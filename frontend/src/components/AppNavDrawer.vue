<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

defineProps<{ modelValue: boolean; rail: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const auth = useAuthStore()
const items = computed(() => [
  { title: 'Книги', icon: 'mdi-book-open-variant', to: '/books' },
  { title: 'Авторы', icon: 'mdi-account-group', to: '/authors' },
  { title: 'Жанры', icon: 'mdi-tag-multiple', to: '/genres' },
  { title: 'Мои списки', icon: 'mdi-format-list-bulleted', to: '/lists' },
  ...(auth.isAdmin ? [
    { title: 'Импорт', icon: 'mdi-database-import', to: '/imports' },
    { title: 'Администрирование', icon: 'mdi-shield-crown', to: '/admin' },
  ] : []),
  { title: 'Конвертация', icon: 'mdi-swap-horizontal', to: '/conversions' },
])
</script>

<template>
  <v-navigation-drawer
    :model-value="modelValue"
    :rail="rail"
    :rail-width="64"
    permanent
    @update:model-value="emit('update:modelValue', $event)"
  >
    <v-list nav>
      <v-list-item
        v-for="item in items"
        :key="item.to"
        :to="item.to"
        :prepend-icon="item.icon"
        :title="item.title"
      >
        <v-tooltip activator="parent" location="end">{{ item.title }}</v-tooltip>
      </v-list-item>
    </v-list>
  </v-navigation-drawer>
</template>
