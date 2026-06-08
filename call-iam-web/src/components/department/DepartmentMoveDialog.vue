<template>
  <div v-if="open" class="dialog-backdrop">
    <section class="dialog-card">
      <header class="dialog-header">
        <h3>{{ title }}</h3>
        <button type="button" class="dialog-close" @click="$emit('close')">关闭</button>
      </header>
      <form class="dialog-body" @submit.prevent="submit">
        <label>
          新上级部门
          <select v-model="parentId">
            <option :value="null">根部门</option>
            <option v-for="department in departments" :key="department.id" :value="department.id">
              {{ department.name }}
            </option>
          </select>
        </label>
        <div class="dialog-actions">
          <button type="button" class="dialog-secondary" @click="$emit('close')">取消</button>
          <button type="submit" class="dialog-primary" :disabled="loading">
            {{ loading ? '提交中...' : '移动' }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';

import type { Department } from '../../api/types';

const props = defineProps<{
    open: boolean;
    title: string;
    loading?: boolean;
    departments: Department[];
    initialParentId?: number | null;
}>();

const emit = defineEmits<{
    close: [];
    submit: [parentId: number | null];
}>();

const parentId = ref<number | null>(null);

watch(
        () => [props.open, props.initialParentId],
        () => {
            if (props.open) {
                parentId.value = props.initialParentId ?? null;
            }
        },
        { immediate: true }
);

function submit() {
    emit('submit', parentId.value);
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

.dialog-body select {
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
