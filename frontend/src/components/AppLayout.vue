<script setup lang="ts">
import { ref } from 'vue'
import { RouterView } from 'vue-router'
import AppHeader from './AppHeader.vue'
import AppNavDrawer from './AppNavDrawer.vue'

const drawer = ref(true)
const rail = ref(false)

function toggleRail() {
  rail.value = !rail.value
  drawer.value = true
}
</script>

<template>
  <div>
    <AppHeader v-model="drawer" :rail="rail" @toggle:rail="toggleRail" />
    <AppNavDrawer v-model="drawer" :rail="rail" />
    <v-main>
      <v-container fluid>
        <RouterView />
      </v-container>
    </v-main>
  </div>
</template>
