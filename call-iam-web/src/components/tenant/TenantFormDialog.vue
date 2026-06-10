<template>
  <div v-if="open" class="dialog-backdrop">
    <section class="dialog-card" @click.stop>
      <header class="dialog-header">
        <div class="dialog-title-block">
          <p class="dialog-kicker">Tenant</p>
          <h3 class="dialog-title">{{ title }}</h3>
          <p class="dialog-description">维护租户基础信息与到期时间，保证租户生命周期配置清晰可追踪。</p>
        </div>
        <button type="button" class="dialog-close" @click="$emit('close')">关闭</button>
      </header>
      <form class="dialog-body" @submit.prevent="submit">
        <label class="dialog-field">
          <span class="dialog-label">租户编码</span>
          <input v-model="form.tenantCode" class="page-input" type="text" placeholder="输入租户编码" :disabled="mode === 'edit'" />
        </label>
        <label class="dialog-field">
          <span class="dialog-label">租户名称</span>
          <input v-model="form.tenantName" class="page-input" type="text" placeholder="输入租户名称" />
        </label>
        <label class="dialog-field">
          <span class="dialog-label">到期时间</span>
          <input v-model="form.expireTime" class="page-input" type="datetime-local" />
        </label>
        <div class="dialog-actions">
          <button type="button" class="dialog-secondary" @click="$emit('close')">取消</button>
          <button type="submit" class="dialog-primary" :disabled="loading">
            {{ loading ? '提交中...' : '保存' }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue';

import type { TenantForm } from '../../api/types';

const props = defineProps<{
    open: boolean;
    title: string;
    mode: 'create' | 'edit';
    loading?: boolean;
    initialValue?: Partial<TenantForm> | null;
}>();

const emit = defineEmits<{
    close: [];
    submit: [payload: TenantForm];
}>();

const form = reactive<TenantForm>({
    tenantCode: '',
    tenantName: '',
    expireTime: null
});

watch(
        () => [props.open, props.initialValue],
        () => {
            if (!props.open) {
                return;
            }
            form.tenantCode = props.initialValue?.tenantCode ?? '';
            form.tenantName = props.initialValue?.tenantName ?? '';
            form.expireTime = props.initialValue?.expireTime ?? null;
        },
        {
            immediate: true
        }
);

function submit() {
    emit('submit', {
        tenantCode: form.tenantCode.trim(),
        tenantName: form.tenantName.trim(),
        expireTime: form.expireTime || null
    });
}
</script>

<style scoped></style>
