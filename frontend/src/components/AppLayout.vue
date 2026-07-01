<script setup lang="ts">
import { ref } from 'vue'
import { RouterView } from 'vue-router'
import AppHeader from './AppHeader.vue'
import AppNavDrawer from './AppNavDrawer.vue'

const drawer = ref(true)
</script>

<template>
  <div>
    <AppHeader v-model="drawer" />
    <AppNavDrawer v-model="drawer" />
    <v-main>
      <v-container fluid>
        <RouterView />
      </v-container>
    </v-main>
  </div>
</template>
