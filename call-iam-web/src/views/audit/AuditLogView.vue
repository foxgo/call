<template>
  <section class="page-shell">
    <header class="page-header">
      <div class="page-title-group">
        <p class="page-eyebrow">Audit</p>
        <h1 class="page-title">审计日志</h1>
        <p class="page-description">查看关键事件、操作者和资源变更，支持按资源类型、资源 ID 和操作者筛选。</p>
      </div>
    </header>

    <section class="page-toolbar surface-panel">
      <div class="page-toolbar__group audit-filter-grid">
        <input v-model="filters.operatorId" class="page-input" type="number" placeholder="操作者 ID" />
        <input v-model="filters.resourceType" class="page-input" type="text" placeholder="资源类型" />
        <input v-model="filters.resourceId" class="page-input" type="text" placeholder="资源 ID" />
      </div>
      <button type="button" class="page-primary-btn" @click="loadAudits">查询</button>
    </section>

    <section class="record-list">
      <article v-for="audit in audits" :key="audit.id" class="record-card surface-panel">
        <div class="record-card__main">
          <div class="record-card__chips">
            <span class="status-pill status-pill--neutral">{{ audit.resourceType }}</span>
            <span class="info-pill">资源 {{ audit.resourceId || '-' }}</span>
          </div>
          <h2 class="record-card__title">{{ audit.action }}</h2>
          <p class="record-card__description">操作者 {{ audit.operatorId ?? '-' }} / {{ audit.createdAt }}</p>
          <p class="record-card__meta">面向关键操作审计与排障回溯。</p>
        </div>
        <div class="record-card__actions">
          <button type="button" class="page-secondary-btn" @click="openDetail(audit.id)">详情</button>
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
.audit-filter-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    width: 100%;
}

@media (max-width: 760px) {
    .audit-filter-grid {
        grid-template-columns: 1fr;
    }
}
</style>
