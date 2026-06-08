<template>
  <main class="login-view">
    <form class="login-card" @submit.prevent="submit" data-testid="login-submit">
      <p class="login-eyebrow">Call IAM</p>
      <h1>登录</h1>
      <p class="login-copy">统一身份中心控制台</p>
      <div class="login-form">
        <label>
          租户编码
          <input v-model="form.tenantCode" data-testid="tenant-code-input" type="text" placeholder="平台管理员可留空" />
        </label>
        <label>
          账号
          <input v-model="form.account" data-testid="account-input" type="text" placeholder="用户名 / 手机号 / 邮箱" />
        </label>
        <label>
          密码
          <input v-model="form.password" data-testid="password-input" type="password" placeholder="请输入密码" />
        </label>
        <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>
        <button type="submit" class="login-submit" :disabled="submitting">
          {{ submitting ? '登录中...' : '进入控制台' }}
        </button>
      </div>
    </form>
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
    display: grid;
    place-items: center;
    padding: 24px;
}

.login-card {
    width: min(420px, 100%);
    padding: 40px 32px;
    border: 1px solid var(--iam-border);
    border-radius: 28px;
    background: var(--iam-surface);
    box-shadow: 0 24px 80px rgba(15, 23, 42, 0.14);
}

.login-eyebrow {
    margin: 0 0 12px;
    font-size: 12px;
    letter-spacing: 0.24em;
    text-transform: uppercase;
    color: var(--iam-accent);
}

h1 {
    margin: 0;
    font-size: 40px;
    line-height: 1.1;
}

.login-copy {
    margin: 12px 0 0;
    color: var(--iam-muted);
}

.login-form {
    display: grid;
    gap: 14px;
    margin-top: 24px;
}

.login-form label {
    display: grid;
    gap: 8px;
    font-size: 14px;
    font-weight: 600;
}

.login-form input {
    padding: 12px 14px;
    border: 1px solid var(--iam-border);
    border-radius: 14px;
    background: rgba(255, 255, 255, 0.96);
}

.login-submit {
    padding: 12px 16px;
    border: 0;
    border-radius: 14px;
    background: #12343b;
    color: #fff;
    cursor: pointer;
}

.login-submit:disabled {
    cursor: wait;
    opacity: 0.7;
}

.login-error {
    margin: 0;
    color: #b42318;
}
</style>
