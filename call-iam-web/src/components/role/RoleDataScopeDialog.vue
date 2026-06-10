<template>
  <div v-if="open" class="dialog-backdrop">
    <section class="dialog-card dialog-card--compact">
      <header class="dialog-header">
        <div class="dialog-title-block">
          <p class="dialog-kicker">Data Scope</p>
          <h3 class="dialog-title">{{ title }}</h3>
          <p class="dialog-description">定义角色可访问的数据范围，用于部门级与个人级数据隔离。</p>
        </div>
        <button type="button" class="dialog-close" @click="$emit('close')">关闭</button>
      </header>
      <form class="dialog-body" @submit.prevent="submit">
        <label class="dialog-field">
          <span class="dialog-label">数据范围</span>
          <select v-model="scopeType" class="page-select">
            <option value="ALL">ALL</option>
            <option value="SELF">SELF</option>
            <option value="DEPARTMENT">DEPARTMENT</option>
            <option value="DEPARTMENT_AND_CHILD">DEPARTMENT_AND_CHILD</option>
          </select>
        </label>
        <label class="dialog-field">
          <span class="dialog-label">部门</span>
          <select v-model="departmentId" class="page-select">
            <option :value="null">不指定</option>
            <option v-for="department in departments" :key="department.id" :value="department.id">
              {{ department.name }}
            </option>
          </select>
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
import { ref, watch } from 'vue';

import type { Department, RoleDataScope } from '../../api/types';

const props = defineProps<{
    open: boolean;
    title: string;
    loading?: boolean;
    departments: Department[];
    initialValue?: RoleDataScope | null;
}>();

const emit = defineEmits<{
    close: [];
    submit: [payload: RoleDataScope];
}>();

const scopeType = ref('ALL');
const departmentId = ref<number | null>(null);

watch(
        () => [props.open, props.initialValue],
        () => {
            if (!props.open) {
                return;
            }
            scopeType.value = props.initialValue?.scopeType ?? 'ALL';
            departmentId.value = props.initialValue?.departmentId ?? null;
        },
        { immediate: true }
);

function submit() {
    emit('submit', {
        scopeType: scopeType.value,
        departmentId: departmentId.value
    });
}
</script>

<style scoped></style>
