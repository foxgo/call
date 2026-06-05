<template>
  <aside class="side-nav">
    <p class="side-nav__eyebrow">Call IAM</p>
    <h1 class="side-nav__title">User Center</h1>
    <nav class="side-nav__links">
      <RouterLink to="/dashboard">仪表盘</RouterLink>
      <RouterLink to="/tenants">租户管理</RouterLink>
      <RouterLink to="/users">用户管理</RouterLink>
      <RouterLink to="/roles">角色权限</RouterLink>
      <RouterLink to="/departments">部门树</RouterLink>
      <RouterLink to="/audits">审计日志</RouterLink>
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
    display: block;
    margin-bottom: 14px;
    color: inherit;
    text-decoration: none;
    font-weight: 600;
    opacity: 0.8;
}

.side-nav__links a.router-link-active {
    opacity: 1;
}
</style>
