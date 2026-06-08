<template>
  <div v-if="open" class="dialog-backdrop">
    <section class="dialog-card">
      <header class="dialog-header">
        <h3>{{ title }}</h3>
        <button type="button" class="dialog-close" @click="$emit('close')">关闭</button>
      </header>
      <form class="dialog-body" @submit.prevent="submit">
        <label>
          角色编码
          <input v-model="form.roleCode" type="text" placeholder="输入角色编码" />
        </label>
        <label>
          角色名称
          <input v-model="form.roleName" type="text" placeholder="输入角色名称" />
        </label>
        <label>
          角色类型
          <select v-model="form.roleType">
            <option value="TENANT_CUSTOM">TENANT_CUSTOM</option>
            <option value="TENANT_SYSTEM">TENANT_SYSTEM</option>
            <option value="PLATFORM_SYSTEM">PLATFORM_SYSTEM</option>
          </select>
        </label>
        <fieldset v-if="permissionOptions.length" class="permission-list">
          <legend>权限</legend>
          <label v-for="permission in permissionOptions" :key="permission.id" class="permission-item">
            <input v-model="form.permissionIds" type="checkbox" :value="permission.id" />
            <span>{{ permission.permissionCode }}</span>
          </label>
        </fieldset>
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
import { computed, reactive, watch } from 'vue';

import type { Permission, RoleForm } from '../../api/types';

const props = defineProps<{
    open: boolean;
    title: string;
    loading?: boolean;
    initialValue?: Partial<RoleForm> | null;
    selectedPermissionIds?: number[];
    permissionOptions?: Permission[];
}>();

const emit = defineEmits<{
    close: [];
    submit: [payload: RoleForm & { permissionIds: number[] }];
}>();

const permissionOptions = computed(() => props.permissionOptions ?? []);

const form = reactive<RoleForm & {
    permissionIds: number[];
}>({
    roleCode: '',
    roleName: '',
    roleType: 'TENANT_CUSTOM',
    permissionIds: []
});

watch(
        () => [props.open, props.initialValue, props.selectedPermissionIds],
        () => {
            if (!props.open) {
                return;
            }
            form.roleCode = props.initialValue?.roleCode ?? '';
            form.roleName = props.initialValue?.roleName ?? '';
            form.roleType = props.initialValue?.roleType ?? 'TENANT_CUSTOM';
            form.permissionIds = [...(props.selectedPermissionIds ?? [])];
        },
        {
            immediate: true
        }
);

function submit() {
    emit('submit', {
        roleCode: form.roleCode.trim(),
        roleName: form.roleName.trim(),
        roleType: form.roleType,
        permissionIds: [...form.permissionIds]
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
    width: 100%;
    padding: 10px 12px;
    border: 1px solid var(--iam-border);
    border-radius: 12px;
}

.permission-list {
    display: grid;
    gap: 12px;
    margin: 0;
    padding: 12px 14px;
    border: 1px solid var(--iam-border);
    border-radius: 16px;
}

.permission-item {
    display: flex;
    gap: 10px;
    align-items: center;
}

.dialog-actions {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
}

.dialog-secondary,
.dialog-primary {
    padding: 10px 14px;
    border-radius: 12px;
    cursor: pointer;
}

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
