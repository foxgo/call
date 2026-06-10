<template>
  <div v-if="open" class="dialog-backdrop">
    <section class="dialog-card">
      <header class="dialog-header">
        <div class="dialog-title-block">
          <p class="dialog-kicker">User</p>
          <h3 class="dialog-title">{{ title }}</h3>
          <p class="dialog-description">维护账号基础信息。创建时设置初始密码，编辑时仅更新联系信息与昵称。</p>
        </div>
        <button type="button" class="dialog-close" @click="$emit('close')">关闭</button>
      </header>
      <form class="dialog-grid" @submit.prevent="submit">
        <label class="dialog-field">
          <span class="dialog-label">用户名</span>
          <input v-model="form.username" class="page-input" type="text" placeholder="输入用户名" :disabled="mode === 'edit'" />
        </label>
        <label class="dialog-field">
          <span class="dialog-label">手机号</span>
          <input v-model="form.mobile" class="page-input" type="text" placeholder="输入手机号" />
        </label>
        <label class="dialog-field">
          <span class="dialog-label">邮箱</span>
          <input v-model="form.email" class="page-input" type="email" placeholder="输入邮箱" />
        </label>
        <label class="dialog-field">
          <span class="dialog-label">昵称</span>
          <input v-model="form.nickname" class="page-input" type="text" placeholder="输入昵称" />
        </label>
        <label v-if="mode === 'create'" class="dialog-field dialog-grid__full">
          <span class="dialog-label">初始密码</span>
          <input v-model="form.password" class="page-input" type="password" placeholder="至少 8 位，包含大小写和数字" />
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

<style scoped></style>
