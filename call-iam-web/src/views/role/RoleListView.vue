<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Role</p>
        <h1>角色权限管理</h1>
      </div>
      <button type="button" class="page-action" @click="dialogOpen = true">新建角色</button>
    </header>

    <section class="table-card">
      <article v-for="role in roles" :key="role.id" class="table-row">
        <div>
          <h2>{{ role.name }}</h2>
          <p>{{ role.scope }}</p>
        </div>
        <strong>{{ role.permissionCount }} 项权限</strong>
      </article>
    </section>

    <RoleFormDialog :open="dialogOpen" title="新建角色" @close="dialogOpen = false" />
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';

import RoleFormDialog from '../../components/role/RoleFormDialog.vue';

const dialogOpen = ref(false);

const roles = [
    { id: 1, name: 'Tenant Admin', scope: '全部部门', permissionCount: 12 },
    { id: 2, name: 'Supervisor', scope: '华东客服一部', permissionCount: 7 }
];
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
</style>
