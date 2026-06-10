<template>
  <header class="header-bar">
    <div class="header-bar__title-group">
      <p class="header-bar__eyebrow">Enterprise Control</p>
      <h2 class="header-bar__title">统一身份中心</h2>
      <p class="header-bar__copy">管理租户、账号、权限与关键审计事件。</p>
    </div>
    <div v-if="authStore.profile" class="header-bar__actions">
      <div class="header-bar__user-card" data-testid="header-user-chip">
        <span class="header-bar__user-label">当前用户</span>
        <strong class="header-bar__user">{{ authStore.profile.displayName }}</strong>
      </div>
      <button type="button" class="page-secondary-btn header-bar__logout" @click="logout">退出登录</button>
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
    align-items: flex-start;
    justify-content: space-between;
    gap: 20px;
    width: min(100%, var(--iam-shell-width));
    margin: 0 auto;
    padding: 28px 28px 0;
}

.header-bar__eyebrow {
    margin: 0 0 8px;
    font-size: 12px;
    letter-spacing: 0.22em;
    text-transform: uppercase;
    color: var(--iam-accent);
    font-weight: 700;
}

.header-bar__title {
    margin: 0;
    font-size: 32px;
    letter-spacing: -0.04em;
}

.header-bar__copy {
    margin: 10px 0 0;
    color: var(--iam-text-muted);
    line-height: 1.6;
}

.header-bar__actions {
    display: flex;
    align-items: center;
    gap: 14px;
}

.header-bar__user-card {
    min-height: 44px;
    padding: 10px 14px;
    border-radius: 16px;
    border: 1px solid var(--iam-border);
    background: rgba(255, 255, 255, 0.82);
    display: grid;
    gap: 2px;
}

.header-bar__user-label {
    color: var(--iam-text-muted);
    font-size: 11px;
    letter-spacing: 0.16em;
    text-transform: uppercase;
}

.header-bar__user {
    font-size: 14px;
}

.header-bar__logout {
    min-width: 112px;
}

@media (max-width: 760px) {
    .header-bar {
        padding: 20px 16px 0;
        flex-direction: column;
    }

    .header-bar__title {
        font-size: 28px;
    }

    .header-bar__actions {
        width: 100%;
        justify-content: space-between;
    }
}
</style>
