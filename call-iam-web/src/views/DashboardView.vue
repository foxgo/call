<template>
  <section class="dashboard-view">
    <header>
      <p class="dashboard-eyebrow">Console</p>
      <h1>仪表盘</h1>
      <p class="dashboard-copy">基于现有 IAM 接口的实时概览。</p>
    </header>

    <section class="dashboard-grid">
      <article class="dashboard-card">
        <span>租户总数</span>
        <strong>{{ summary.tenants }}</strong>
      </article>
      <article class="dashboard-card">
        <span>用户总数</span>
        <strong>{{ summary.users }}</strong>
      </article>
      <article class="dashboard-card">
        <span>角色总数</span>
        <strong>{{ summary.roles }}</strong>
      </article>
      <article class="dashboard-card">
        <span>审计记录</span>
        <strong>{{ summary.audits }}</strong>
      </article>
    </section>

    <section class="dashboard-card">
      <h2>最近审计动作</h2>
      <ul v-if="recentAudits.length" class="dashboard-list">
        <li v-for="audit in recentAudits" :key="audit.id">
          <span>{{ audit.action }}</span>
          <small>{{ audit.createdAt }}</small>
        </li>
      </ul>
      <p v-else class="dashboard-empty">暂无审计记录</p>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';

import { auditApi } from '../api/audit';
import { roleApi } from '../api/role';
import { tenantApi } from '../api/tenant';
import { userApi } from '../api/user';
import type { AuditLog } from '../api/types';

const summary = reactive({
    tenants: 0,
    users: 0,
    roles: 0,
    audits: 0
});
const recentAudits = ref<AuditLog[]>([]);

onMounted(async () => {
    const [tenants, users, roles, audits] = await Promise.all([
        tenantApi.list().catch(() => []),
        userApi.list().catch(() => []),
        roleApi.list().catch(() => []),
        auditApi.list().catch(() => [])
    ]);

    summary.tenants = tenants.length;
    summary.users = users.length;
    summary.roles = roles.length;
    summary.audits = audits.length;
    recentAudits.value = audits.slice(0, 5);
});
</script>

<style scoped>
.dashboard-view {
    display: grid;
    gap: 20px;
}

.dashboard-card {
    padding: 32px;
    border-radius: 24px;
    background: var(--iam-surface);
    border: 1px solid var(--iam-border);
}

.dashboard-eyebrow {
    margin: 0 0 12px;
    font-size: 12px;
    letter-spacing: 0.2em;
    text-transform: uppercase;
    color: var(--iam-accent);
}

h1 {
    margin: 0;
}

.dashboard-copy {
    margin: 12px 0 0;
    color: var(--iam-muted);
}

.dashboard-grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 16px;
}

.dashboard-card strong {
    display: block;
    margin-top: 12px;
    font-size: 36px;
}

.dashboard-list {
    display: grid;
    gap: 12px;
    margin: 16px 0 0;
    padding: 0;
    list-style: none;
}

.dashboard-list li {
    display: flex;
    justify-content: space-between;
    gap: 16px;
}

.dashboard-empty {
    margin: 16px 0 0;
    color: var(--iam-muted);
}

@media (max-width: 900px) {
    .dashboard-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }
}
</style>
