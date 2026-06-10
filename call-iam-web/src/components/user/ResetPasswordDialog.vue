<template>
  <div v-if="open" class="dialog-backdrop">
    <section class="dialog-card dialog-card--compact">
      <header class="dialog-header">
        <div class="dialog-title-block">
          <p class="dialog-kicker">Security</p>
          <h3 class="dialog-title">{{ title }}</h3>
          <p class="dialog-description">设置新的登录密码，建议符合大小写字母与数字混合的复杂度要求。</p>
        </div>
        <button type="button" class="dialog-close" @click="$emit('close')">关闭</button>
      </header>
      <form class="dialog-body" @submit.prevent="submit">
        <label class="dialog-field">
          <span class="dialog-label">新密码</span>
          <input v-model="password" class="page-input" type="password" placeholder="至少 8 位，包含大小写和数字" />
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

<style scoped></style>
