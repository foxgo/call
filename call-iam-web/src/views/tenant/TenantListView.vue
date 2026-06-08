<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Tenant</p>
        <h1>租户管理</h1>
      </div>
      <button type="button" class="page-action" @click="openCreateDialog">新建租户</button>
    </header>

    <section class="toolbar">
      <input v-model="keyword" type="text" placeholder="搜索租户名称或编码" />
      <span class="pager">共 {{ filteredTenants.length }} 个租户</span>
    </section>

    <section class="table-card">
      <article v-for="tenant in filteredTenants" :key="tenant.id" class="table-row">
        <div>
          <h2>{{ tenant.tenantName }}</h2>
          <p>{{ tenant.tenantCode }} / 到期 {{ tenant.expireTime || '-' }}</p>
        </div>
        <div class="row-actions">
          <strong>{{ tenant.status }}</strong>
          <button type="button" @click="openEditDialog(tenant)">编辑</button>
          <button type="button" @click="removeTenant(tenant.id)">删除</button>
        </div>
      </article>
    </section>

    <TenantFormDialog
      :open="dialogOpen"
      :title="editingTenant ? '编辑租户' : '新建租户'"
      :mode="editingTenant ? 'edit' : 'create'"
      :loading="submitting"
      :initial-value="editingTenant"
      @close="closeDialog"
      @submit="submitTenant"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import { tenantApi } from '../../api/tenant';
import type { Tenant, TenantForm } from '../../api/types';
import TenantFormDialog from '../../components/tenant/TenantFormDialog.vue';

const dialogOpen = ref(false);
const submitting = ref(false);
const keyword = ref('');
const editingTenant = ref<Tenant | null>(null);
const tenants = ref<Tenant[]>([]);

const filteredTenants = computed(() =>
        tenants.value.filter((tenant) =>
                `${tenant.tenantName} ${tenant.tenantCode}`.toLowerCase().includes(keyword.value.toLowerCase())
        )
);

onMounted(loadTenants);

async function loadTenants() {
    tenants.value = await tenantApi.list();
}

function openCreateDialog() {
    editingTenant.value = null;
    dialogOpen.value = true;
}

function openEditDialog(tenant: Tenant) {
    editingTenant.value = tenant;
    dialogOpen.value = true;
}

function closeDialog() {
    dialogOpen.value = false;
    editingTenant.value = null;
}

async function submitTenant(payload: TenantForm) {
    submitting.value = true;
    try {
        if (editingTenant.value) {
            await tenantApi.update(editingTenant.value.id, {
                tenantName: payload.tenantName,
                expireTime: payload.expireTime
            });
        } else {
            await tenantApi.create(payload);
        }
        await loadTenants();
        closeDialog();
    } finally {
        submitting.value = false;
    }
}

async function removeTenant(tenantId: number) {
    if (typeof window !== 'undefined' && window.confirm && !window.confirm('确认删除该租户吗？')) {
        return;
    }
    await tenantApi.remove(tenantId);
    await loadTenants();
}
</script>

<style scoped>
.page-shell {
    display: grid;
    gap: 20px;
}

.page-header,
.toolbar,
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

.page-action,
.toolbar input {
    padding: 10px 14px;
    border-radius: 12px;
    border: 1px solid var(--iam-border);
}

.page-action {
    background: #12343b;
    color: #fff;
    cursor: pointer;
}

.toolbar input {
    min-width: 240px;
}

.pager {
    color: var(--iam-muted);
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
    border: 1px solid var(--iam-border);
    border-radius: 10px;
    background: transparent;
    cursor: pointer;
}
</style>
