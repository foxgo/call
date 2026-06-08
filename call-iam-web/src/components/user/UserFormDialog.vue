<template>
  <div v-if="open" class="dialog-backdrop">
    <section class="dialog-card">
      <header class="dialog-header">
        <h3>{{ title }}</h3>
        <button type="button" class="dialog-close" @click="$emit('close')">关闭</button>
      </header>
      <form class="dialog-grid" @submit.prevent="submit">
        <label>
          用户名
          <input v-model="form.username" type="text" placeholder="输入用户名" :disabled="mode === 'edit'" />
        </label>
        <label>
          手机号
          <input v-model="form.mobile" type="text" placeholder="输入手机号" />
        </label>
        <label>
          邮箱
          <input v-model="form.email" type="email" placeholder="输入邮箱" />
        </label>
        <label>
          昵称
          <input v-model="form.nickname" type="text" placeholder="输入昵称" />
        </label>
        <label v-if="mode === 'create'" class="dialog-grid__full">
          初始密码
          <input v-model="form.password" type="password" placeholder="至少 8 位，包含大小写和数字" />
        </label>
        <div class="dialog-actions dialog-grid__full">
          <button type="button" class="dialog-secondary" @click="$emit('close')">取消</button>
          <button type="submit" class="dialog-primary" :disabled="loading">
            {{ loading ? '提交中...' : '保存' }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue';

import type { UserForm, UserUpdateForm } from '../../api/types';

const props = defineProps<{
    open: boolean;
    title: string;
    mode: 'create' | 'edit';
    loading?: boolean;
    initialValue?: Partial<UserForm & UserUpdateForm> | null;
}>();

const emit = defineEmits<{
    close: [];
    submit: [payload: UserForm | UserUpdateForm];
}>();

const form = reactive<UserForm>({
    username: '',
    mobile: '',
    email: '',
    password: '',
    nickname: ''
});

watch(
        () => [props.open, props.initialValue, props.mode],
        () => {
            if (!props.open) {
                return;
            }
            form.username = props.initialValue?.username ?? '';
            form.mobile = props.initialValue?.mobile ?? '';
            form.email = props.initialValue?.email ?? '';
            form.password = '';
            form.nickname = props.initialValue?.nickname ?? '';
        },
        {
            immediate: true
        }
);

function submit() {
    if (props.mode === 'create') {
        emit('submit', {
            username: form.username.trim(),
            mobile: form.mobile.trim(),
            email: form.email.trim(),
            password: form.password,
            nickname: form.nickname.trim()
        });
        return;
    }
    emit('submit', {
        mobile: form.mobile.trim(),
        email: form.email.trim(),
        nickname: form.nickname.trim()
    });
}
</script>

<style scoped>
.dialog-backdrop {
    position: fixed;
    inset: 0;
    display: grid;
    place-items: center;
    padding: 24px;
    background: rgba(15, 23, 42, 0.2);
}

.dialog-card {
    width: min(560px, 100%);
    padding: 24px;
    border-radius: 24px;
    background: #fff;
    box-shadow: 0 24px 80px rgba(15, 23, 42, 0.18);
}

.dialog-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
}

.dialog-close {
    border: 0;
    background: transparent;
    color: var(--iam-accent);
    cursor: pointer;
}

.dialog-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
    margin-top: 16px;
}

.dialog-grid label {
    display: grid;
    gap: 8px;
}

.dialog-grid input {
    width: 100%;
    padding: 10px 12px;
    border: 1px solid var(--iam-border);
    border-radius: 12px;
}

.dialog-grid__full {
    grid-column: 1 / -1;
}

.dialog-actions {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
}

.dialog-secondary,
.dialog-primary {
    padding: 10px 14px;
    border-radius: 12px;
    cursor: pointer;
}

.dialog-secondary {
    border: 1px solid var(--iam-border);
    background: transparent;
}

.dialog-primary {
    border: 0;
    background: #12343b;
    color: #fff;
}
</style>
