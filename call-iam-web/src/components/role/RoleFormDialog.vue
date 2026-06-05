<template>
  <div v-if="open" class="dialog-backdrop">
    <section class="dialog-card">
      <header class="dialog-header">
        <h3>{{ title }}</h3>
        <button type="button" class="dialog-close" @click="$emit('close')">关闭</button>
      </header>
      <div class="permission-list">
        <label v-for="permission in permissions" :key="permission" class="permission-item">
          <input type="checkbox" />
          <span>{{ permission }}</span>
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

const permissions = [
    'iam:user:create',
    'iam:user:update',
    'iam:role:scope'
];
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

.permission-list {
    display: grid;
    gap: 12px;
    margin-top: 16px;
}

.permission-item {
    display: flex;
    gap: 10px;
    align-items: center;
}
</style>
