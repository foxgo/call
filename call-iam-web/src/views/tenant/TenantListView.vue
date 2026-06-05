<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Tenant</p>
        <h1>租户管理</h1>
      </div>
      <button type="button" class="page-action" @click="dialogOpen = true">新建租户</button>
    </header>

    <section class="toolbar">
      <input v-model="keyword" type="text" placeholder="搜索租户名称" />
      <span class="pager">第 1 / 3 页</span>
    </section>

    <section class="table-card">
      <article v-for="tenant in filteredTenants" :key="tenant.id" class="table-row">
        <div>
          <h2>{{ tenant.name }}</h2>
          <p>{{ tenant.owner }}</p>
        </div>
        <strong>{{ tenant.status }}</strong>
      </article>
    </section>

    <TenantFormDialog open-title="新建租户" :open="dialogOpen" title="新建租户" @close="dialogOpen = false" />
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';

import TenantFormDialog from '../../components/tenant/TenantFormDialog.vue';

const dialogOpen = ref(false);
const keyword = ref('');

const tenants = [
    { id: 1, name: 'Acme Cloud', owner: 'Alice Chen', status: 'ACTIVE' },
    { id: 2, name: 'Northwind Contact Center', owner: 'Bob Wang', status: 'ACTIVE' },
    { id: 3, name: 'Helios BPO', owner: 'Carla Xu', status: 'PENDING' }
];

const filteredTenants = computed(() =>
        tenants.filter((tenant) =>
                tenant.name.toLowerCase().includes(keyword.value.toLowerCase())
        )
);
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
</style>
