<template>
  <div class="console-layout">
    <SideNav />
    <div class="console-shell">
      <HeaderBar />
      <main class="console-content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { RouterView } from 'vue-router';

import HeaderBar from '../components/HeaderBar.vue';
import SideNav from '../components/SideNav.vue';
</script>
