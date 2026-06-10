<template>
  <aside class="side-nav">
    <div class="side-nav__brand surface-panel">
      <p class="side-nav__eyebrow">Call IAM</p>
      <h1 class="side-nav__title">Identity Console</h1>
      <p class="side-nav__copy">统一身份、租户治理与审计控制台。</p>
    </div>
    <nav class="side-nav__links" data-testid="shell-nav">
      <RouterLink v-for="item in navItems" :key="item.to" :to="item.to" class="side-nav__link">
        <span class="side-nav__link-title">{{ item.label }}</span>
        <small class="side-nav__link-copy">{{ item.copy }}</small>
      </RouterLink>
    </nav>
  </aside>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router';

const navItems = [
    {
        to: '/dashboard',
        label: '仪表盘',
        copy: '查看系统总览与近期活动'
    },
    {
        to: '/tenants',
        label: '租户管理',
        copy: '维护租户生命周期与到期状态'
    },
    {
        to: '/users',
        label: '用户管理',
        copy: '管理账号、角色与部门归属'
    },
    {
        to: '/roles',
        label: '角色权限',
        copy: '统一配置角色、权限与数据范围'
    },
    {
        to: '/departments',
        label: '部门树',
        copy: '组织结构与层级调整'
    },
    {
        to: '/audits',
        label: '审计日志',
        copy: '追踪关键操作与事件详情'
    }
];
</script>

<style scoped>
.side-nav {
    padding: 24px;
    display: grid;
    gap: 18px;
    border-right: 1px solid rgba(128, 152, 166, 0.18);
    background: linear-gradient(180deg, rgba(241, 247, 250, 0.82) 0%, rgba(233, 241, 246, 0.9) 100%);
    backdrop-filter: blur(18px);
}

.side-nav__brand {
    padding: 22px;
}

.side-nav__eyebrow {
    margin: 0 0 10px;
    font-size: 12px;
    letter-spacing: 0.22em;
    text-transform: uppercase;
    color: var(--iam-accent);
    font-weight: 700;
}

.side-nav__title {
    margin: 0;
    font-size: 28px;
    line-height: 1.05;
    letter-spacing: -0.04em;
}

.side-nav__copy {
    margin: 10px 0 0;
    color: var(--iam-text-muted);
    line-height: 1.6;
}

.side-nav__links {
    display: grid;
    gap: 10px;
}

.side-nav__link {
    display: grid;
    gap: 5px;
    padding: 14px 16px;
    border-radius: 18px;
    color: var(--iam-text-soft);
    text-decoration: none;
    border: 1px solid transparent;
    transition: transform 180ms ease, border-color 180ms ease, background-color 180ms ease, color 180ms ease;
}

.side-nav__link:hover {
    transform: translateY(-1px);
    border-color: rgba(31, 111, 120, 0.14);
    background: rgba(255, 255, 255, 0.66);
}

.side-nav__link-title {
    font-weight: 700;
    color: var(--iam-text);
}

.side-nav__link-copy {
    color: var(--iam-text-muted);
    font-size: 12px;
    line-height: 1.45;
}

.side-nav__link.router-link-active {
    border-color: rgba(31, 111, 120, 0.18);
    background: rgba(255, 255, 255, 0.88);
    box-shadow: inset 0 0 0 1px rgba(31, 111, 120, 0.06);
}

@media (max-width: 1100px) {
    .side-nav {
        border-right: 0;
        border-bottom: 1px solid rgba(128, 152, 166, 0.18);
    }
}
</style>
