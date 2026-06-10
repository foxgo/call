<template>
  <div v-if="open" class="dialog-backdrop">
    <section class="dialog-card dialog-card--compact">
      <header class="dialog-header">
        <div class="dialog-title-block">
          <p class="dialog-kicker">Department Move</p>
          <h3 class="dialog-title">{{ title }}</h3>
          <p class="dialog-description">重新设置部门父级节点，用于组织结构迁移和树形层级调整。</p>
        </div>
        <button type="button" class="dialog-close" @click="$emit('close')">关闭</button>
      </header>
      <form class="dialog-body" @submit.prevent="submit">
        <label class="dialog-field">
          <span class="dialog-label">新上级部门</span>
          <select v-model="parentId" class="page-select">
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

<style scoped></style>
