<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Audit</p>
        <h1>审计日志</h1>
      </div>
    </header>

    <section class="filter-card">
      <input v-model="filters.operatorId" type="number" placeholder="操作者 ID" />
      <input v-model="filters.resourceType" type="text" placeholder="资源类型" />
      <input v-model="filters.resourceId" type="text" placeholder="资源 ID" />
      <button type="button" class="page-action" @click="loadAudits">查询</button>
    </section>

    <section class="table-card">
      <article v-for="audit in audits" :key="audit.id" class="table-row">
        <div>
          <h2>{{ audit.action }}</h2>
          <p>{{ audit.resourceType }} / {{ audit.resourceId || '-' }} / {{ audit.createdAt }}</p>
        </div>
        <div class="row-actions">
          <strong>{{ audit.operatorId ?? '-' }}</strong>
          <button type="button" @click="openDetail(audit.id)">详情</button>
        </div>
      </article>
    </section>

    <AuditDetailDialog :open="detailOpen" :audit="selectedAudit" @close="detailOpen = false" />
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';

import { auditApi } from '../../api/audit';
import type { AuditLog } from '../../api/types';
import AuditDetailDialog from '../../components/audit/AuditDetailDialog.vue';

const filters = reactive({
    operatorId: '',
    resourceType: '',
    resourceId: ''
});
const audits = ref<AuditLog[]>([]);
const selectedAudit = ref<AuditLog | null>(null);
const detailOpen = ref(false);

onMounted(loadAudits);

async function loadAudits() {
    audits.value = await auditApi.list({
        operatorId: filters.operatorId ? Number(filters.operatorId) : undefined,
        resourceType: filters.resourceType || undefined,
        resourceId: filters.resourceId || undefined
    });
}

async function openDetail(auditId: number) {
    selectedAudit.value = await auditApi.get(auditId);
    detailOpen.value = true;
}
</script>

<style scoped>
.page-shell {
    display: grid;
    gap: 20px;
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

.filter-card,
.table-row {
    display: flex;
    align-items: center;
    gap: 16px;
}

.filter-card input,
.page-action {
    padding: 10px 14px;
    border-radius: 12px;
    border: 1px solid var(--iam-border);
}

.page-action {
    background: #12343b;
    color: #fff;
    cursor: pointer;
}

.table-row {
    justify-content: space-between;
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
    border-radius: 10px;
    border: 1px solid var(--iam-border);
    background: transparent;
    cursor: pointer;
}
</style>
