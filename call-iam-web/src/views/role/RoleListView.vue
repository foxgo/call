<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Role</p>
        <h1>角色权限管理</h1>
      </div>
      <button type="button" class="page-action" @click="openCreateDialog">新建角色</button>
    </header>

    <section class="table-card">
      <article v-for="role in roles" :key="role.id" class="table-row">
        <div>
          <h2>{{ role.roleName }}</h2>
          <p>{{ role.roleCode }} / {{ role.roleType }}</p>
        </div>
        <div class="row-actions">
          <strong>{{ role.permissionIds.length }} 项权限</strong>
          <button type="button" @click="openEditDialog(role)">编辑</button>
          <button type="button" @click="openDataScopeDialog(role)">数据范围</button>
          <button type="button" @click="removeRole(role.id)">删除</button>
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

<style scoped>
.page-shell {
    display: grid;
    gap: 20px;
}

.page-header,
.table-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
}

.page-eyebrow {
    margin: 0 0 8px;
    font-size: 12px;
    letter-spacing: 0.22em;
    text-transform: uppercase;
    color: var(--iam-accent);
}

h1,
h2,
p {
    margin: 0;
}

.page-action {
    padding: 10px 14px;
    border-radius: 12px;
    border: 1px solid var(--iam-border);
    background: #12343b;
    color: #fff;
    cursor: pointer;
}

.table-card {
    display: grid;
    gap: 12px;
}

.table-row {
    padding: 18px 20px;
    border-radius: 20px;
    background: var(--iam-surface);
    border: 1px solid var(--iam-border);
}

.row-actions {
    display: flex;
    align-items: center;
    gap: 12px;
}

.row-actions button {
    padding: 8px 12px;
    border-radius: 10px;
    border: 1px solid var(--iam-border);
    background: transparent;
    cursor: pointer;
}
</style>
