<template>
  <div v-if="open" class="dialog-backdrop">
    <section class="dialog-card dialog-card--compact">
      <header class="dialog-header">
        <div class="dialog-title-block">
          <p class="dialog-kicker">Role Assignment</p>
          <h3 class="dialog-title">{{ title }}</h3>
          <p class="dialog-description">为当前账号分配一个或多个角色，角色决定功能权限和数据访问范围。</p>
        </div>
        <button type="button" class="dialog-close" @click="$emit('close')">关闭</button>
      </header>
      <form class="dialog-body" @submit.prevent="submit">
        <div class="selection-list">
          <label v-for="role in roles" :key="role.id" class="selection-item">
            <input v-model="selectedIds" type="checkbox" :value="role.id" />
            <span>{{ role.roleName }}</span>
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

import type { Role } from '../../api/types';

const props = defineProps<{
    open: boolean;
    title: string;
    loading?: boolean;
    roles: Role[];
    selectedRoleIds: number[];
}>();

const emit = defineEmits<{
    close: [];
    submit: [roleIds: number[]];
}>();

const selectedIds = ref<number[]>([]);

watch(
        () => [props.open, props.selectedRoleIds],
        () => {
            if (props.open) {
                selectedIds.value = [...props.selectedRoleIds];
            }
        },
        { immediate: true }
);

function submit() {
    emit('submit', [...selectedIds.value]);
}
</script>

<style scoped></style>
