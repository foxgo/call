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

<style scoped>
.console-layout {
    min-height: 100vh;
    display: grid;
    grid-template-columns: 280px 1fr;
    background:
        radial-gradient(circle at top left, rgba(15, 118, 110, 0.12), transparent 32%),
        linear-gradient(135deg, #f7f1e7 0%, #dde8ef 100%);
}

.console-shell {
    min-width: 0;
    display: flex;
    flex-direction: column;
}

.console-content {
    flex: 1;
    padding: 24px;
}

@media (max-width: 900px) {
    .console-layout {
        grid-template-columns: 1fr;
    }
}
</style>
