<template>
  <div v-if="open" class="dialog-backdrop">
    <section class="dialog-card dialog-card--compact">
      <header class="dialog-header">
        <div class="dialog-title-block">
          <p class="dialog-kicker">Department Assignment</p>
          <h3 class="dialog-title">{{ title }}</h3>
          <p class="dialog-description">为账号分配部门归属，影响组织范围和后续数据权限计算。</p>
        </div>
        <button type="button" class="dialog-close" @click="$emit('close')">关闭</button>
      </header>
      <form class="dialog-body" @submit.prevent="submit">
        <div class="selection-list">
          <label v-for="department in departments" :key="department.id" class="selection-item">
            <input v-model="selectedIds" type="checkbox" :value="department.id" />
            <span>{{ department.name }}</span>
          </label>
        </div>
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
import { ref, watch } from 'vue';

import type { Department } from '../../api/types';

const props = defineProps<{
    open: boolean;
    title: string;
    loading?: boolean;
    departments: Department[];
    selectedDepartmentIds: number[];
}>();

const emit = defineEmits<{
    close: [];
    submit: [departmentIds: number[]];
}>();

const selectedIds = ref<number[]>([]);

watch(
        () => [props.open, props.selectedDepartmentIds],
        () => {
            if (props.open) {
                selectedIds.value = [...props.selectedDepartmentIds];
            }
        },
        { immediate: true }
);

function submit() {
    emit('submit', [...selectedIds.value]);
}
</script>

<style scoped></style>
