<template>
  <section class="page-shell">
    <header class="page-header">
      <div class="page-title-group">
        <p class="page-eyebrow">User</p>
        <h1 class="page-title">用户管理</h1>
        <p class="page-description">统一管理用户账号、启停状态、角色授权、部门归属与密码重置。</p>
      </div>
      <button data-testid="open-create-user" type="button" class="page-primary-btn" @click="openCreateDialog">
        新建用户
      </button>
    </header>

    <section class="page-toolbar surface-panel">
      <div class="page-toolbar__group">
        <select v-model="selectedDepartmentId" class="page-select user-filter" @change="loadUsers">
          <option value="">全部部门</option>
          <option v-for="department in departments" :key="department.id" :value="department.id">
            {{ department.name }}
          </option>
        </select>
      </div>
      <span class="page-toolbar__meta">共 {{ users.length }} 个用户</span>
    </section>

    <section class="record-list">
      <article v-for="user in users" :key="user.id" class="record-card surface-panel">
        <div class="record-card__main">
          <div class="record-card__chips">
            <span :class="['status-pill', statusClass(user.status)]">{{ user.status }}</span>
            <span class="info-pill">{{ user.username }}</span>
          </div>
          <h2 class="record-card__title">{{ user.nickname }}</h2>
          <p class="record-card__description">{{ user.email || user.mobile || '-' }}</p>
          <p class="record-card__meta">角色 {{ user.roleIds.length }} 个 / 部门 {{ user.departmentIds.length }} 个</p>
        </div>
        <div class="record-card__actions">
          <button type="button" class="page-secondary-btn" @click="openEditDialog(user)">编辑</button>
          <button type="button" class="page-secondary-btn" @click="toggleStatus(user)">
            {{ user.status === 'ENABLE' ? '停用' : '启用' }}
          </button>
          <button type="button" class="page-secondary-btn" @click="openRolesDialog(user)">角色</button>
          <button type="button" class="page-secondary-btn" @click="openDepartmentsDialog(user)">部门</button>
          <button type="button" class="page-secondary-btn" @click="openPasswordDialog(user)">重置密码</button>
          <button type="button" class="page-danger-btn" @click="removeUser(user.id)">删除</button>
        </div>
      </article>
    </section>

    <UserFormDialog
      :open="dialogOpen"
      :title="editingUser ? '编辑用户' : '新建用户'"
      :mode="editingUser ? 'edit' : 'create'"
      :loading="submitting"
      :initial-value="editingUser ?? undefined"
      @close="closeDialogs"
      @submit="submitUser"
    />
    <UserRolesDialog
      :open="rolesDialogOpen"
      title="分配角色"
      :loading="submitting"
      :roles="roles"
      :selected-role-ids="selectedUser?.roleIds ?? []"
      @close="closeDialogs"
      @submit="submitUserRoles"
    />
    <UserDepartmentsDialog
      :open="departmentsDialogOpen"
      title="分配部门"
      :loading="submitting"
      :departments="departments"
      :selected-department-ids="selectedUser?.departmentIds ?? []"
      @close="closeDialogs"
      @submit="submitUserDepartments"
    />
    <ResetPasswordDialog
      :open="passwordDialogOpen"
      title="重置密码"
      :loading="submitting"
      @close="closeDialogs"
      @submit="submitResetPassword"
    />
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { departmentApi } from '../../api/department';
import { roleApi } from '../../api/role';
import type { Department, Role, User, UserForm, UserUpdateForm } from '../../api/types';
import { userApi } from '../../api/user';
import ResetPasswordDialog from '../../components/user/ResetPasswordDialog.vue';
import UserFormDialog from '../../components/user/UserFormDialog.vue';
import UserDepartmentsDialog from '../../components/user/UserDepartmentsDialog.vue';
import UserRolesDialog from '../../components/user/UserRolesDialog.vue';

const dialogOpen = ref(false);
const rolesDialogOpen = ref(false);
const departmentsDialogOpen = ref(false);
const passwordDialogOpen = ref(false);
const submitting = ref(false);
const selectedDepartmentId = ref<string>('');
const users = ref<User[]>([]);
const roles = ref<Role[]>([]);
const departments = ref<Department[]>([]);
const editingUser = ref<User | null>(null);
const selectedUser = ref<User | null>(null);

onMounted(async () => {
    await Promise.all([loadUsers(), loadRoles(), loadDepartments()]);
});

async function loadUsers() {
    users.value = await userApi.list({
        departmentId: selectedDepartmentId.value ? Number(selectedDepartmentId.value) : undefined
    });
}

async function loadRoles() {
    roles.value = await roleApi.list();
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

function openEditDialog(user: User) {
    editingUser.value = user;
    selectedUser.value = user;
    dialogOpen.value = true;
}

function openCreateDialog() {
    editingUser.value = null;
    selectedUser.value = null;
    dialogOpen.value = true;
}

function openRolesDialog(user: User) {
    selectedUser.value = user;
    rolesDialogOpen.value = true;
}

function openDepartmentsDialog(user: User) {
    selectedUser.value = user;
    departmentsDialogOpen.value = true;
}

function openPasswordDialog(user: User) {
    selectedUser.value = user;
    passwordDialogOpen.value = true;
}

function closeDialogs() {
    dialogOpen.value = false;
    rolesDialogOpen.value = false;
    departmentsDialogOpen.value = false;
    passwordDialogOpen.value = false;
    editingUser.value = null;
    selectedUser.value = null;
}

async function submitUser(payload: UserForm | UserUpdateForm) {
    submitting.value = true;
    try {
        if (editingUser.value) {
            await userApi.update(editingUser.value.id, payload as UserUpdateForm);
        } else {
            await userApi.create(payload as UserForm);
        }
        await loadUsers();
        closeDialogs();
    } finally {
        submitting.value = false;
    }
}

async function toggleStatus(user: User) {
    await userApi.updateStatus(user.id, user.status === 'ENABLE' ? 'DISABLE' : 'ENABLE');
    await loadUsers();
}

async function submitUserRoles(roleIds: number[]) {
    if (!selectedUser.value) {
        return;
    }
    submitting.value = true;
    try {
        await userApi.assignRoles(selectedUser.value.id, roleIds);
        await loadUsers();
        closeDialogs();
    } finally {
        submitting.value = false;
    }
}

async function submitUserDepartments(departmentIds: number[]) {
    if (!selectedUser.value) {
        return;
    }
    submitting.value = true;
    try {
        await userApi.assignDepartments(selectedUser.value.id, departmentIds);
        await loadUsers();
        closeDialogs();
    } finally {
        submitting.value = false;
    }
}

async function submitResetPassword(password: string) {
    if (!selectedUser.value) {
        return;
    }
    submitting.value = true;
    try {
        await userApi.resetPassword(selectedUser.value.id, password);
        closeDialogs();
    } finally {
        submitting.value = false;
    }
}

async function removeUser(userId: number) {
    if (typeof window !== 'undefined' && window.confirm && !window.confirm('确认删除该用户吗？')) {
        return;
    }
    await userApi.remove(userId);
    await loadUsers();
}

function statusClass(status: string) {
    if (status === 'ENABLE') {
        return 'status-pill--success';
    }
    if (status === 'DISABLE') {
        return 'status-pill--warning';
    }
    if (status === 'LOCK') {
        return 'status-pill--danger';
    }
    return 'status-pill--neutral';
}
</script>

<style scoped>
.user-filter {
    min-width: min(280px, 100%);
}
</style>
