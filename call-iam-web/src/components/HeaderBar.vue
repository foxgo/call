<template>
  <header class="header-bar">
    <div>
      <p class="header-bar__eyebrow">Console</p>
      <h2 class="header-bar__title">统一身份中心</h2>
    </div>
    <div v-if="authStore.profile" class="header-bar__actions">
      <span class="header-bar__user">{{ authStore.profile.displayName }}</span>
      <button type="button" class="header-bar__logout" @click="logout">退出登录</button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';

import { useAuthStore } from '../stores/auth';

const authStore = useAuthStore();
const router = useRouter();

async function logout() {
    authStore.logout();
    await router.push('/login');
}
</script>

<style scoped>
.header-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 24px 24px 0;
}

.header-bar__eyebrow {
    margin: 0 0 6px;
    font-size: 12px;
    letter-spacing: 0.22em;
    text-transform: uppercase;
    color: var(--iam-accent);
}

.header-bar__title {
    margin: 0;
    font-size: 28px;
}

.header-bar__actions {
    display: flex;
    align-items: center;
    gap: 14px;
}

.header-bar__user {
    font-weight: 600;
}

.header-bar__logout {
    padding: 9px 14px;
    border: 1px solid var(--iam-border);
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.7);
    cursor: pointer;
}
</style>
