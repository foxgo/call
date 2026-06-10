<template>
  <section class="page-shell">
    <header class="page-header">
      <div class="page-title-group">
        <p class="page-eyebrow">Tenant</p>
        <h1 class="page-title">租户管理</h1>
        <p class="page-description">统一查看租户状态、租户编码和到期时间，面向平台级治理与续期管理。</p>
      </div>
      <button type="button" class="page-primary-btn" @click="openCreateDialog">新建租户</button>
    </header>

    <section class="page-toolbar surface-panel">
      <div class="page-toolbar__group">
        <input
          v-model="keyword"
          data-testid="tenant-search-input"
          class="page-input tenant-search"
          type="text"
          placeholder="搜索租户名称或编码"
        />
      </div>
      <span class="page-toolbar__meta">共 {{ filteredTenants.length }} 个租户</span>
    </section>

    <section class="record-list">
      <article v-for="tenant in filteredTenants" :key="tenant.id" class="record-card surface-panel">
        <div class="record-card__main">
          <div class="record-card__chips">
            <span :class="['status-pill', statusClass(tenant.status)]">{{ tenant.status }}</span>
            <span class="info-pill">编码 {{ tenant.tenantCode }}</span>
          </div>
          <h2 class="record-card__title">{{ tenant.tenantName }}</h2>
          <p class="record-card__description">到期时间 {{ tenant.expireTime || '未设置' }}</p>
          <p class="record-card__meta">适用于平台级租户生命周期、续期和基本治理场景。</p>
        </div>
        <div class="record-card__actions">
          <button type="button" class="page-secondary-btn" @click="openEditDialog(tenant)">编辑</button>
          <button type="button" class="page-danger-btn" @click="removeTenant(tenant.id)">删除</button>
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

function statusClass(status: string) {
    if (status === 'ACTIVE') {
        return 'status-pill--success';
    }
    if (status === 'DISABLE' || status === 'DISABLED') {
        return 'status-pill--warning';
    }
    return 'status-pill--neutral';
}
</script>

<style scoped>
.tenant-search {
    min-width: min(360px, 100%);
}

@media (max-width: 760px) {
    .tenant-search {
        min-width: 100%;
    }
}
</style>
