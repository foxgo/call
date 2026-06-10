<template>
  <section class="page-shell">
    <header class="page-header">
      <div class="page-title-group">
        <p class="page-eyebrow">Role</p>
        <h1 class="page-title">角色权限管理</h1>
        <p class="page-description">维护系统角色、权限项与数据范围，保证多租户权限边界清晰可控。</p>
      </div>
      <button type="button" class="page-primary-btn" @click="openCreateDialog">新建角色</button>
    </header>

    <section class="record-list">
      <article v-for="role in roles" :key="role.id" class="record-card surface-panel">
        <div class="record-card__main">
          <div class="record-card__chips">
            <span class="status-pill status-pill--neutral">{{ role.roleType }}</span>
            <span class="info-pill">{{ role.roleCode }}</span>
          </div>
          <h2 class="record-card__title">{{ role.roleName }}</h2>
          <p class="record-card__description">{{ role.permissionIds.length }} 项权限配置</p>
          <p class="record-card__meta">适用于平台与租户级别的授权控制和数据范围管理。</p>
        </div>
        <div class="record-card__actions">
          <button type="button" class="page-secondary-btn" @click="openEditDialog(role)">编辑</button>
          <button type="button" class="page-secondary-btn" @click="openDataScopeDialog(role)">数据范围</button>
          <button type="button" class="page-danger-btn" @click="removeRole(role.id)">删除</button>
        </div>
      </article>
    </section>

    <RoleFormDialog
      :open="dialogOpen"
      :title="editingRole ? '编辑角色' : '新建角色'"
      :loading="submitting"
      :initial-value="editingRole ?? undefined"
      :selected-permission-ids="editingRole?.permissionIds ?? []"
      :permission-options="permissions"
      @close="closeDialogs"
      @submit="submitRole"
    />
    <RoleDataScopeDialog
      :open="dataScopeDialogOpen"
      title="设置数据范围"
      :loading="submitting"
      :departments="departments"
      :initial-value="editingRole?.dataScope ?? null"
      @close="closeDialogs"
      @submit="submitDataScope"
    />
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { departmentApi } from '../../api/department';
import { roleApi } from '../../api/role';
import type { Department, Permission, Role, RoleDataScope } from '../../api/types';
import RoleFormDialog from '../../components/role/RoleFormDialog.vue';
import RoleDataScopeDialog from '../../components/role/RoleDataScopeDialog.vue';

const dialogOpen = ref(false);
const dataScopeDialogOpen = ref(false);
const submitting = ref(false);
const roles = ref<Role[]>([]);
const permissions = ref<Permission[]>([]);
const departments = ref<Department[]>([]);
const editingRole = ref<Role | null>(null);

onMounted(async () => {
    await Promise.all([loadRoles(), loadPermissions(), loadDepartments()]);
});

async function loadRoles() {
    roles.value = await roleApi.list();
}

async function loadPermissions() {
    permissions.value = await roleApi.listPermissions();
}

async function loadDepartments() {
    const tree = await departmentApi.tree();
    departments.value = flattenDepartments(tree);
}

function flattenDepartments(nodes: Array<Department & { children?: any[] }>, output: Department[] = []): Department[] {
    nodes.forEach((node) => {
        output.push({
            id: node.id,
            parentId: node.parentId,
            name: node.name,
            status: node.status,
            sort: node.sort
        });
        flattenDepartments(node.children ?? [], output);
    });
    return output;
}

function openEditDialog(role: Role) {
    editingRole.value = role;
    dialogOpen.value = true;
}

function openCreateDialog() {
    editingRole.value = null;
    dialogOpen.value = true;
}

function openDataScopeDialog(role: Role) {
    editingRole.value = role;
    dataScopeDialogOpen.value = true;
}

function closeDialogs() {
    dialogOpen.value = false;
    dataScopeDialogOpen.value = false;
    editingRole.value = null;
}

async function submitRole(payload: {
    roleCode: string;
    roleName: string;
    roleType: string;
    permissionIds: number[];
}) {
    submitting.value = true;
    try {
        let roleId = editingRole.value?.id;
        if (editingRole.value) {
            await roleApi.update(editingRole.value.id, {
                roleCode: payload.roleCode,
                roleName: payload.roleName,
                roleType: payload.roleType
            });
        } else {
            const created = await roleApi.create({
                roleCode: payload.roleCode,
                roleName: payload.roleName,
                roleType: payload.roleType
            });
            roleId = created.id;
        }
        if (roleId) {
            await roleApi.updatePermissions(roleId, payload.permissionIds);
        }
        await loadRoles();
        closeDialogs();
    } finally {
        submitting.value = false;
    }
}

async function submitDataScope(payload: RoleDataScope) {
    if (!editingRole.value) {
        return;
    }
    submitting.value = true;
    try {
        await roleApi.updateDataScope(editingRole.value.id, payload);
        await loadRoles();
        closeDialogs();
    } finally {
        submitting.value = false;
    }
}

async function removeRole(roleId: number) {
    if (typeof window !== 'undefined' && window.confirm && !window.confirm('确认删除该角色吗？')) {
        return;
    }
    await roleApi.remove(roleId);
    await loadRoles();
}
</script>

<style scoped></style>
