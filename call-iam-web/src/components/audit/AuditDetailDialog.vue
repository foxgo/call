<template>
  <div v-if="open" class="dialog-backdrop">
    <section class="dialog-card">
      <header class="dialog-header">
        <div class="dialog-title-block">
          <p class="dialog-kicker">Audit Detail</p>
          <h3 class="dialog-title">审计详情</h3>
          <p class="dialog-description">查看单条审计事件的操作者、动作、资源和发生时间。</p>
        </div>
        <button type="button" class="dialog-close" @click="$emit('close')">关闭</button>
      </header>
      <dl v-if="audit" class="detail-grid">
        <div class="detail-grid__item">
          <dt>ID</dt>
          <dd>{{ audit.id }}</dd>
        </div>
        <div class="detail-grid__item">
          <dt>操作人</dt>
          <dd>{{ audit.operatorId ?? '-' }}</dd>
        </div>
        <div class="detail-grid__item">
          <dt>动作</dt>
          <dd>{{ audit.action }}</dd>
        </div>
        <div class="detail-grid__item">
          <dt>资源类型</dt>
          <dd>{{ audit.resourceType }}</dd>
        </div>
        <div class="detail-grid__item">
          <dt>资源 ID</dt>
          <dd>{{ audit.resourceId ?? '-' }}</dd>
        </div>
        <div class="detail-grid__item">
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

<style scoped></style>
