<template>
  <div v-if="open" class="dialog-backdrop">
    <section class="dialog-card">
      <header class="dialog-header">
        <h3>审计详情</h3>
        <button type="button" class="dialog-close" @click="$emit('close')">关闭</button>
      </header>
      <dl v-if="audit" class="detail-grid">
        <div>
          <dt>ID</dt>
          <dd>{{ audit.id }}</dd>
        </div>
        <div>
          <dt>操作人</dt>
          <dd>{{ audit.operatorId ?? '-' }}</dd>
        </div>
        <div>
          <dt>动作</dt>
          <dd>{{ audit.action }}</dd>
        </div>
        <div>
          <dt>资源类型</dt>
          <dd>{{ audit.resourceType }}</dd>
        </div>
        <div>
          <dt>资源 ID</dt>
          <dd>{{ audit.resourceId ?? '-' }}</dd>
        </div>
        <div>
          <dt>时间</dt>
          <dd>{{ audit.createdAt }}</dd>
        </div>
      </dl>
    </section>
  </div>
</template>

<script setup lang="ts">
import type { AuditLog } from '../../api/types';

defineProps<{
    open: boolean;
    audit: AuditLog | null;
}>();

defineEmits<{
    close: [];
}>();
</script>

<style scoped>
.dialog-backdrop {
    position: fixed;
    inset: 0;
    display: grid;
    place-items: center;
    padding: 24px;
    background: rgba(15, 23, 42, 0.2);
}

.dialog-card {
    width: min(520px, 100%);
    padding: 24px;
    border-radius: 24px;
    background: #fff;
}

.dialog-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
}

.dialog-close {
    padding: 10px 14px;
    border: 1px solid var(--iam-border);
    border-radius: 12px;
    background: transparent;
    cursor: pointer;
}

.detail-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
    margin: 20px 0 0;
}

.detail-grid dt {
    font-size: 12px;
    color: var(--iam-muted);
}

.detail-grid dd {
    margin: 6px 0 0;
    font-weight: 600;
}
</style>
