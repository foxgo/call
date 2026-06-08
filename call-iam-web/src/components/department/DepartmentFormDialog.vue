<template>
  <div v-if="open" class="dialog-backdrop">
    <section class="dialog-card">
      <header class="dialog-header">
        <h3>{{ title }}</h3>
        <button type="button" class="dialog-close" @click="$emit('close')">关闭</button>
      </header>
      <form class="dialog-body" @submit.prevent="submit">
        <label v-if="mode === 'create'">
          上级部门
          <select v-model="form.parentId">
            <option :value="null">根部门</option>
            <option v-for="department in departments" :key="department.id" :value="department.id">
              {{ department.name }}
            </option>
          </select>
        </label>
        <label>
          名称
          <input v-model="form.name" type="text" placeholder="输入部门名称" />
        </label>
        <label>
          状态
          <select v-model="form.status">
            <option value="ACTIVE">ACTIVE</option>
            <option value="DISABLED">DISABLED</option>
          </select>
        </label>
        <label>
          排序
          <input v-model.number="form.sort" type="number" min="0" />
        </label>
        <div class="dialog-actions">
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

import type { Department, DepartmentForm } from '../../api/types';

const props = defineProps<{
    open: boolean;
    title: string;
    mode: 'create' | 'edit';
    loading?: boolean;
    departments: Department[];
    initialValue?: Partial<DepartmentForm> | null;
}>();

const emit = defineEmits<{
    close: [];
    submit: [payload: DepartmentForm];
}>();

const form = reactive<DepartmentForm>({
    parentId: null,
    name: '',
    status: 'ACTIVE',
    sort: 0
});

watch(
        () => [props.open, props.initialValue],
        () => {
            if (!props.open) {
                return;
            }
            form.parentId = props.initialValue?.parentId ?? null;
            form.name = props.initialValue?.name ?? '';
            form.status = props.initialValue?.status ?? 'ACTIVE';
            form.sort = props.initialValue?.sort ?? 0;
        },
        { immediate: true }
);

function submit() {
    emit('submit', {
        parentId: form.parentId,
        name: form.name.trim(),
        status: form.status,
        sort: Number(form.sort) || 0
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
    width: min(480px, 100%);
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

.dialog-body input,
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
