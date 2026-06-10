<template>
  <div v-if="open" class="dialog-backdrop">
    <section class="dialog-card dialog-card--wide">
      <header class="dialog-header">
        <div class="dialog-title-block">
          <p class="dialog-kicker">Role</p>
          <h3 class="dialog-title">{{ title }}</h3>
          <p class="dialog-description">配置角色基础属性和权限项，作为 IAM 授权体系中的核心控制单元。</p>
        </div>
        <button type="button" class="dialog-close" @click="$emit('close')">关闭</button>
      </header>
      <form class="dialog-body" @submit.prevent="submit">
        <label class="dialog-field">
          <span class="dialog-label">角色编码</span>
          <input v-model="form.roleCode" class="page-input" type="text" placeholder="输入角色编码" />
        </label>
        <label class="dialog-field">
          <span class="dialog-label">角色名称</span>
          <input v-model="form.roleName" class="page-input" type="text" placeholder="输入角色名称" />
        </label>
        <label class="dialog-field">
          <span class="dialog-label">角色类型</span>
          <select v-model="form.roleType" class="page-select">
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
.permission-list {
    display: grid;
    gap: 12px;
    margin: 0;
    padding: 16px;
    border: 1px solid var(--iam-border);
    border-radius: 18px;
    background: var(--iam-surface-muted);
}

.permission-item {
    display: flex;
    gap: 10px;
    align-items: center;
}
</style>
