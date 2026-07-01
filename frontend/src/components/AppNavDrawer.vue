<script setup lang="ts">
defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const items = [
  { title: 'Книги', icon: 'mdi-book-open-variant', to: '/books' },
  { title: 'Авторы', icon: 'mdi-account-group', to: '/authors' },
  { title: 'Жанры', icon: 'mdi-tag-multiple', to: '/genres' },
  { title: 'Мои списки', icon: 'mdi-format-list-bulleted', to: '/lists' },
  { title: 'Импорт', icon: 'mdi-database-import', to: '/imports' },
  { title: 'Конвертация', icon: 'mdi-swap-horizontal', to: '/conversions' },
]
</script>

<template>
  <v-navigation-drawer
    :model-value="modelValue"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <v-list nav>
      <v-list-item
        v-for="item in items"
        :key="item.to"
        :to="item.to"
        :prepend-icon="item.icon"
        :title="item.title"
      />
    </v-list>
  </v-navigation-drawer>
</template>
