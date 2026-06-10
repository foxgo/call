<template>
  <main class="login-view">
    <section class="login-shell">
      <article class="login-hero surface-panel" data-testid="login-hero">
        <p class="login-eyebrow">Call IAM</p>
        <h1>统一身份与租户治理</h1>
        <p class="login-copy">
          面向多租户联络中心的企业级身份控制台，集中管理账号、组织、角色权限与关键审计事件。
        </p>
        <div class="login-highlights">
          <div class="login-highlight">
            <strong>集中治理</strong>
            <span>统一管理租户生命周期、组织结构与账号状态。</span>
          </div>
          <div class="login-highlight">
            <strong>安全审计</strong>
            <span>关键动作可回溯，支持角色与数据范围的细粒度控制。</span>
          </div>
          <div class="login-highlight">
            <strong>现代后台</strong>
            <span>简洁、稳定、适合长期运营维护的 SaaS 控制台体验。</span>
          </div>
        </div>
      </article>

      <form class="login-card surface-panel" @submit.prevent="submit" data-testid="login-submit">
        <div class="login-card__header">
          <p class="login-card__eyebrow">Secure Access</p>
          <h2>登录控制台</h2>
          <p class="login-card__copy">使用租户账号进入统一身份中心。</p>
        </div>

        <div class="login-form">
          <label class="dialog-field">
            <span class="dialog-label">租户编码</span>
            <input
              v-model="form.tenantCode"
              data-testid="tenant-code-input"
              class="page-input"
              type="text"
              placeholder="平台管理员可留空"
            />
          </label>
          <label class="dialog-field">
            <span class="dialog-label">账号</span>
            <input
              v-model="form.account"
              data-testid="account-input"
              class="page-input"
              type="text"
              placeholder="用户名 / 手机号 / 邮箱"
            />
          </label>
          <label class="dialog-field">
            <span class="dialog-label">密码</span>
            <input
              v-model="form.password"
              data-testid="password-input"
              class="page-input"
              type="password"
              placeholder="请输入密码"
            />
          </label>
          <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>
          <button type="submit" class="page-primary-btn login-submit" :disabled="submitting">
            {{ submitting ? '登录中...' : '进入控制台' }}
          </button>
        </div>
      </form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useAuthStore } from '../stores/auth';

const authStore = useAuthStore();
const router = useRouter();
const submitting = ref(false);
const errorMessage = ref('');
const form = reactive({
    tenantCode: '',
    account: '',
    password: ''
});

async function submit() {
    submitting.value = true;
    errorMessage.value = '';
    try {
        await authStore.login({
            tenantCode: form.tenantCode.trim() || undefined,
            account: form.account.trim(),
            password: form.password
        });
        await router.push('/dashboard');
    } catch (error: any) {
        errorMessage.value = error?.response?.data?.message ?? '登录失败，请检查账号信息';
    } finally {
        submitting.value = false;
    }
}
</script>

<style scoped>
.login-view {
    min-height: 100vh;
    padding: 24px;
    display: grid;
    place-items: center;
    background: var(--iam-bg-strong), var(--iam-bg);
}

.login-shell {
    width: min(1120px, 100%);
    display: grid;
    grid-template-columns: minmax(0, 1.1fr) minmax(360px, 0.9fr);
    gap: 22px;
    align-items: stretch;
}

.login-hero {
    padding: 38px;
    display: grid;
    align-content: center;
    gap: 20px;
}

.login-eyebrow {
    margin: 0;
    font-size: 12px;
    letter-spacing: 0.24em;
    text-transform: uppercase;
    color: var(--iam-accent);
    font-weight: 700;
}

.login-hero h1,
.login-card h2 {
    margin: 0;
    line-height: 1.06;
    letter-spacing: -0.04em;
}

.login-hero h1 {
    font-size: 52px;
}

.login-copy {
    margin: 0;
    max-width: 580px;
    color: var(--iam-text-soft);
    font-size: 17px;
    line-height: 1.7;
}

.login-highlights {
    display: grid;
    gap: 14px;
}

.login-highlight {
    padding: 18px 20px;
    border-radius: 20px;
    border: 1px solid var(--iam-border);
    background: rgba(255, 255, 255, 0.72);
    display: grid;
    gap: 6px;
}

.login-highlight strong {
    font-size: 16px;
}

.login-highlight span {
    color: var(--iam-text-muted);
    line-height: 1.6;
}

.login-card {
    width: min(460px, 100%);
    padding: 30px;
    justify-self: end;
}

.login-card__header {
    display: grid;
    gap: 10px;
}

.login-card__eyebrow {
    margin: 0;
    color: var(--iam-accent);
    text-transform: uppercase;
    letter-spacing: 0.18em;
    font-size: 12px;
    font-weight: 700;
}

.login-card__copy {
    margin: 0;
    color: var(--iam-text-muted);
    line-height: 1.6;
}

.login-form {
    display: grid;
    gap: 16px;
    margin-top: 24px;
}

.login-submit {
    width: 100%;
}

.login-error {
    margin: 0;
    padding: 12px 14px;
    border-radius: 14px;
    color: var(--iam-danger);
    background: rgba(160, 64, 67, 0.1);
    border: 1px solid rgba(160, 64, 67, 0.14);
}

@media (max-width: 900px) {
    .login-shell {
        grid-template-columns: 1fr;
    }

    .login-card {
        justify-self: stretch;
        width: 100%;
    }

    .login-hero {
        padding: 28px;
    }

    .login-hero h1 {
        font-size: 40px;
    }
}
</style>
