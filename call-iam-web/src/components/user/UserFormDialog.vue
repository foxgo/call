<template>
  <div v-if="open" class="dialog-backdrop">
    <section class="dialog-card">
      <header class="dialog-header">
        <h3>{{ title }}</h3>
        <button type="button" class="dialog-close" @click="$emit('close')">关闭</button>
      </header>
      <div class="dialog-grid">
        <label>
          用户名
          <input type="text" placeholder="输入用户名" />
        </label>
        <label>
          手机号
          <input type="text" placeholder="输入手机号" />
        </label>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
defineProps<{
    open: boolean;
    title: string;
}>();

defineEmits<{
    close: [];
}>();
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
</style>
