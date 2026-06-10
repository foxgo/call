<template>
  <section class="page-shell">
    <header class="page-header">
      <div class="page-title-group">
        <p class="page-eyebrow">Department</p>
        <h1 class="page-title">部门管理</h1>
        <p class="page-description">维护组织层级、部门状态与父子关系，支持子部门创建与节点移动。</p>
      </div>
      <button type="button" class="page-primary-btn" @click="openCreateDialog()">新建部门</button>
    </header>

    <section class="record-list">
      <article
        v-for="item in flatDepartments"
        :key="item.id"
        class="record-card surface-panel department-row"
        :style="{ marginLeft: `${item.depth * 18}px` }"
      >
        <div class="record-card__main">
          <div class="record-card__chips">
            <span :class="['status-pill', statusClass(item.status)]">{{ item.status }}</span>
            <span class="info-pill">排序 {{ item.sort }}</span>
          </div>
          <h2 class="record-card__title">{{ item.name }}</h2>
          <p class="record-card__meta">层级深度 {{ item.depth }} / 父级 {{ item.parentId ?? 'ROOT' }}</p>
        </div>
        <div class="record-card__actions">
          <button type="button" class="page-secondary-btn" @click="openCreateDialog(item.id)">新增子部门</button>
          <button type="button" class="page-secondary-btn" @click="openEditDialog(item)">编辑</button>
          <button type="button" class="page-secondary-btn" @click="openMoveDialog(item)">移动</button>
          <button type="button" class="page-danger-btn" @click="removeDepartment(item.id)">删除</button>
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

function statusClass(status: string) {
    return status === 'ACTIVE' ? 'status-pill--success' : 'status-pill--warning';
}
</script>

<style scoped>
.department-row {
    position: relative;
}

.department-row::before {
    content: '';
    position: absolute;
    left: -12px;
    top: 18px;
    bottom: 18px;
    width: 2px;
    border-radius: 999px;
    background: rgba(31, 111, 120, 0.16);
}

@media (max-width: 760px) {
    .department-row {
        margin-left: 0 !important;
    }

    .department-row::before {
        display: none;
    }
}
</style>
