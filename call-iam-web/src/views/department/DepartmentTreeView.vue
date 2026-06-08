<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Department</p>
        <h1>部门管理</h1>
      </div>
      <button type="button" class="page-action" @click="openCreateDialog()">新建部门</button>
    </header>

    <section class="tree-card">
      <article v-for="item in flatDepartments" :key="item.id" class="tree-row" :style="{ paddingLeft: `${item.depth * 24 + 16}px` }">
        <div>
          <strong>{{ item.name }}</strong>
          <p>{{ item.status }} / sort {{ item.sort }}</p>
        </div>
        <div class="row-actions">
          <button type="button" @click="openCreateDialog(item.id)">新增子部门</button>
          <button type="button" @click="openEditDialog(item)">编辑</button>
          <button type="button" @click="openMoveDialog(item)">移动</button>
          <button type="button" @click="removeDepartment(item.id)">删除</button>
        </div>
      </article>
    </section>

    <DepartmentFormDialog
      :open="dialogOpen"
      :title="editingDepartment ? '编辑部门' : '新建部门'"
      :mode="editingDepartment ? 'edit' : 'create'"
      :loading="submitting"
      :departments="flatDepartments"
      :initial-value="formInitialValue"
      @close="closeDialogs"
      @submit="submitDepartment"
    />
    <DepartmentMoveDialog
      :open="moveDialogOpen"
      title="移动部门"
      :loading="submitting"
      :departments="flatDepartments.filter((department) => department.id !== editingDepartment?.id)"
      :initial-parent-id="editingDepartment?.parentId ?? null"
      @close="closeDialogs"
      @submit="submitMove"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import { departmentApi } from '../../api/department';
import type { Department, DepartmentForm, DepartmentTreeNode } from '../../api/types';
import DepartmentFormDialog from '../../components/department/DepartmentFormDialog.vue';
import DepartmentMoveDialog from '../../components/department/DepartmentMoveDialog.vue';

const dialogOpen = ref(false);
const moveDialogOpen = ref(false);
const submitting = ref(false);
const departmentTree = ref<DepartmentTreeNode[]>([]);
const editingDepartment = ref<Department | null>(null);
const createParentId = ref<number | null>(null);

const flatDepartments = computed(() => flattenTree(departmentTree.value));
const formInitialValue = computed(() => {
    if (editingDepartment.value) {
        return editingDepartment.value;
    }
    return {
        parentId: createParentId.value,
        name: '',
        status: 'ACTIVE',
        sort: 0
    };
});

onMounted(loadTree);

async function loadTree() {
    departmentTree.value = await departmentApi.tree();
}

function flattenTree(nodes: DepartmentTreeNode[], depth = 0, output: Array<Department & {
    depth: number;
}> = []) {
    nodes.forEach((node) => {
        output.push({
            id: node.id,
            parentId: node.parentId,
            name: node.name,
            status: node.status,
            sort: node.sort,
            depth
        });
        flattenTree(node.children, depth + 1, output);
    });
    return output;
}

function openCreateDialog(parentId: number | null = null) {
    editingDepartment.value = null;
    createParentId.value = parentId;
    dialogOpen.value = true;
}

function openEditDialog(department: Department) {
    editingDepartment.value = department;
    dialogOpen.value = true;
}

function openMoveDialog(department: Department) {
    editingDepartment.value = department;
    moveDialogOpen.value = true;
}

function closeDialogs() {
    dialogOpen.value = false;
    moveDialogOpen.value = false;
    editingDepartment.value = null;
    createParentId.value = null;
}

async function submitDepartment(payload: DepartmentForm) {
    submitting.value = true;
    try {
        if (editingDepartment.value) {
            await departmentApi.update(editingDepartment.value.id, {
                name: payload.name,
                status: payload.status,
                sort: payload.sort
            });
        } else {
            await departmentApi.create(payload);
        }
        await loadTree();
        closeDialogs();
    } finally {
        submitting.value = false;
    }
}

async function submitMove(parentId: number | null) {
    if (!editingDepartment.value) {
        return;
    }
    submitting.value = true;
    try {
        await departmentApi.move(editingDepartment.value.id, parentId);
        await loadTree();
        closeDialogs();
    } finally {
        submitting.value = false;
    }
}

async function removeDepartment(departmentId: number) {
    if (typeof window !== 'undefined' && window.confirm && !window.confirm('确认删除该部门吗？')) {
        return;
    }
    await departmentApi.remove(departmentId);
    await loadTree();
}
</script>

<style scoped>
.page-shell {
    display: grid;
    gap: 20px;
}

.page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
}

.page-eyebrow {
    margin: 0 0 8px;
    font-size: 12px;
    letter-spacing: 0.22em;
    text-transform: uppercase;
    color: var(--iam-accent);
}

h1 {
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

.tree-card {
    display: grid;
    gap: 10px;
    padding: 16px;
    border-radius: 20px;
    background: var(--iam-surface);
    border: 1px solid var(--iam-border);
}

.tree-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 14px 12px;
    border-radius: 16px;
    background: rgba(255, 255, 255, 0.8);
}

.tree-row p,
.tree-row strong {
    margin: 0;
}

.tree-row p {
    color: var(--iam-muted);
}

.row-actions {
    display: flex;
    gap: 10px;
}

.row-actions button {
    padding: 8px 12px;
    border: 1px solid var(--iam-border);
    border-radius: 10px;
    background: transparent;
    cursor: pointer;
}
</style>
