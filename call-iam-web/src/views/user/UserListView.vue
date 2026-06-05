<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">User</p>
        <h1>用户管理</h1>
      </div>
      <button data-testid="open-create-user" type="button" class="page-action" @click="dialogOpen = true">
        新建用户
      </button>
    </header>

    <section class="table-card">
      <article v-for="user in users" :key="user.id" class="table-row">
        <div>
          <h2>{{ user.name }}</h2>
          <p>{{ user.department }}</p>
        </div>
        <strong>{{ user.role }}</strong>
      </article>
    </section>

    <UserFormDialog :open="dialogOpen" title="新建用户" @close="dialogOpen = false" />
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';

import UserFormDialog from '../../components/user/UserFormDialog.vue';

const dialogOpen = ref(false);

const users = [
    { id: 1, name: '林霄', department: '平台运营中心', role: 'Tenant Admin' },
    { id: 2, name: '苏航', department: '华东客服一部', role: 'Supervisor' }
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
