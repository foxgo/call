<template>
  <div v-if="open" class="dialog-backdrop">
    <section class="dialog-card">
      <header class="dialog-header">
        <div class="dialog-title-block">
          <p class="dialog-kicker">Department</p>
          <h3 class="dialog-title">{{ title }}</h3>
          <p class="dialog-description">设置部门名称、状态、排序与父级关系，保证组织结构维护一致且可预期。</p>
        </div>
        <button type="button" class="dialog-close" @click="$emit('close')">关闭</button>
      </header>
      <form class="dialog-body" @submit.prevent="submit">
        <label v-if="mode === 'create'" class="dialog-field">
          <span class="dialog-label">上级部门</span>
          <select v-model="form.parentId" class="page-select">
            <option :value="null">根部门</option>
            <option v-for="department in departments" :key="department.id" :value="department.id">
              {{ department.name }}
            </option>
          </select>
        </label>
        <label class="dialog-field">
          <span class="dialog-label">名称</span>
          <input v-model="form.name" class="page-input" type="text" placeholder="输入部门名称" />
        </label>
        <label class="dialog-field">
          <span class="dialog-label">状态</span>
          <select v-model="form.status" class="page-select">
            <option value="ACTIVE">ACTIVE</option>
            <option value="DISABLED">DISABLED</option>
          </select>
        </label>
        <label class="dialog-field">
          <span class="dialog-label">排序</span>
          <input v-model.number="form.sort" class="page-input" type="number" min="0" />
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

<style scoped></style>
