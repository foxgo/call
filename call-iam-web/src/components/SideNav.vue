<template>
  <aside class="side-nav">
    <p class="side-nav__eyebrow">Call IAM</p>
    <h1 class="side-nav__title">User Center</h1>
    <nav class="side-nav__links">
      <RouterLink to="/dashboard">仪表盘</RouterLink>
    </nav>
  </aside>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router';
</script>

<style scoped>
.side-nav {
    padding: 32px 24px;
    color: #f8fafc;
    background: linear-gradient(180deg, #12343b 0%, #0b2230 100%);
}

.side-nav__eyebrow {
    margin: 0 0 12px;
    font-size: 12px;
    letter-spacing: 0.24em;
    text-transform: uppercase;
    opacity: 0.72;
}

.side-nav__title {
    margin: 0 0 32px;
    font-size: 32px;
}

.side-nav__links a {
    color: inherit;
    text-decoration: none;
    font-weight: 600;
}
</style>
