<template>
  <section class="dashboard-view">
    <header class="page-header">
      <div class="page-title-group">
        <p class="page-eyebrow">Overview</p>
        <h1 class="page-title">仪表盘</h1>
        <p class="page-description">聚焦租户、账号、角色与审计事件的实时概览，帮助运营团队快速判断系统状态。</p>
      </div>
    </header>

    <section class="split-panel">
      <article class="surface-panel dashboard-hero">
        <div class="dashboard-hero__copy">
          <p class="dashboard-hero__eyebrow">Identity Control</p>
          <h2>统一管理租户、账号与权限</h2>
          <p>
            当前控制台已汇总所有核心维度。你可以从这里快速进入租户、用户、角色、部门与审计日志，处理日常治理工作。
          </p>
        </div>
        <div class="dashboard-hero__facts">
          <div>
            <strong>{{ summary.tenants }}</strong>
            <span>活跃租户视图</span>
          </div>
          <div>
            <strong>{{ summary.audits }}</strong>
            <span>近期审计记录</span>
          </div>
        </div>
      </article>

      <article class="surface-panel section-card">
        <div class="section-card__header">
          <div>
            <h2 class="section-card__title">最近审计动作</h2>
            <p class="section-card__copy">查看最新事件，快速定位系统变更与高风险操作。</p>
          </div>
        </div>
        <ul v-if="recentAudits.length" class="dashboard-list">
          <li v-for="audit in recentAudits" :key="audit.id" class="dashboard-list__item">
            <div>
              <strong>{{ audit.action }}</strong>
              <p>{{ audit.resourceType }} / {{ audit.resourceId || '-' }}</p>
            </div>
            <small>{{ audit.createdAt }}</small>
          </li>
        </ul>
        <p v-else class="empty-state">暂无审计记录</p>
      </article>
    </section>

    <section class="metric-grid">
      <article class="surface-panel metric-card">
        <span class="metric-card__label">租户总数</span>
        <strong class="metric-card__value">{{ summary.tenants }}</strong>
        <span class="metric-card__hint">平台级租户数量</span>
      </article>
      <article class="surface-panel metric-card">
        <span class="metric-card__label">用户总数</span>
        <strong class="metric-card__value">{{ summary.users }}</strong>
        <span class="metric-card__hint">包含启用与待治理账号</span>
      </article>
      <article class="surface-panel metric-card">
        <span class="metric-card__label">角色总数</span>
        <strong class="metric-card__value">{{ summary.roles }}</strong>
        <span class="metric-card__hint">覆盖系统与租户角色</span>
      </article>
      <article class="surface-panel metric-card">
        <span class="metric-card__label">审计记录</span>
        <strong class="metric-card__value">{{ summary.audits }}</strong>
        <span class="metric-card__hint">保留关键操作轨迹</span>
      </article>
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

.dashboard-hero {
    padding: 26px;
    display: grid;
    gap: 26px;
}

.dashboard-hero__copy {
    display: grid;
    gap: 12px;
}

.dashboard-hero__eyebrow {
    margin: 0;
    color: var(--iam-accent);
    letter-spacing: 0.2em;
    text-transform: uppercase;
    font-size: 12px;
    font-weight: 700;
}

.dashboard-hero h2,
.dashboard-hero p {
    margin: 0;
}

.dashboard-hero h2 {
    font-size: 30px;
    line-height: 1.1;
    letter-spacing: -0.04em;
}

.dashboard-hero p {
    color: var(--iam-text-soft);
    line-height: 1.7;
}

.dashboard-hero__facts {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
}

.dashboard-hero__facts div {
    padding: 18px;
    border-radius: 20px;
    background: rgba(255, 255, 255, 0.76);
    border: 1px solid var(--iam-border);
    display: grid;
    gap: 8px;
}

.dashboard-hero__facts strong {
    font-size: 34px;
    line-height: 1;
}

.dashboard-hero__facts span {
    color: var(--iam-text-muted);
}

.dashboard-list {
    display: grid;
    gap: 12px;
    margin: 0;
    padding: 0;
    list-style: none;
}

.dashboard-list__item {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    padding: 16px 18px;
    border-radius: 18px;
    background: var(--iam-surface-muted);
    border: 1px solid var(--iam-border);
}

.dashboard-list__item strong,
.dashboard-list__item p,
.dashboard-list__item small {
    margin: 0;
}

.dashboard-list__item p,
.dashboard-list__item small {
    color: var(--iam-text-muted);
}

@media (max-width: 760px) {
    .dashboard-hero__facts {
        grid-template-columns: 1fr;
    }
}
</style>
