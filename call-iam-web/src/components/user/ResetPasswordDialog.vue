<template>
  <div v-if="open" class="dialog-backdrop">
    <section class="dialog-card">
      <header class="dialog-header">
        <h3>{{ title }}</h3>
        <button type="button" class="dialog-close" @click="$emit('close')">关闭</button>
      </header>
      <form class="dialog-body" @submit.prevent="submit">
        <label>
          新密码
          <input v-model="password" type="password" placeholder="至少 8 位，包含大小写和数字" />
        </label>
        <div class="dialog-actions">
          <button type="button" class="dialog-secondary" @click="$emit('close')">取消</button>
          <button type="submit" class="dialog-primary" :disabled="loading">
            {{ loading ? '提交中...' : '重置密码' }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';

const props = defineProps<{
    open: boolean;
    title: string;
    loading?: boolean;
}>();

const emit = defineEmits<{
    close: [];
    submit: [password: string];
}>();

const password = ref('');

watch(
        () => props.open,
        (open) => {
            if (open) {
                password.value = '';
            }
        },
        { immediate: true }
);

function submit() {
    emit('submit', password.value);
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
    width: min(420px, 100%);
    padding: 24px;
    border-radius: 24px;
    background: #fff;
}

.dialog-header,
.dialog-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
}

.dialog-body {
    display: grid;
    gap: 14px;
    margin-top: 16px;
}

.dialog-body label {
    display: grid;
    gap: 8px;
}

.dialog-body input {
    padding: 10px 12px;
    border: 1px solid var(--iam-border);
    border-radius: 12px;
}

.dialog-actions {
    justify-content: flex-end;
    gap: 12px;
}

.dialog-secondary,
.dialog-primary,
.dialog-close {
    padding: 10px 14px;
    border-radius: 12px;
    cursor: pointer;
}

.dialog-close,
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
