<template>
  <div v-if="open" class="dialog-backdrop">
    <section class="dialog-card" @click.stop>
      <header class="dialog-header">
        <h3>{{ title }}</h3>
        <button type="button" class="dialog-close" @click="$emit('close')">关闭</button>
      </header>
      <form class="dialog-body" @submit.prevent="submit">
        <label>
          租户编码
          <input v-model="form.tenantCode" type="text" placeholder="输入租户编码" :disabled="mode === 'edit'" />
        </label>
        <label>
          租户名称
          <input v-model="form.tenantName" type="text" placeholder="输入租户名称" />
        </label>
        <label>
          到期时间
          <input v-model="form.expireTime" type="datetime-local" />
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
    width: min(480px, 100%);
    padding: 24px;
    border-radius: 24px;
    background: #fff;
    box-shadow: 0 24px 80px rgba(15, 23, 42, 0.18);
}

.dialog-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
}

.dialog-close {
    border: 0;
    background: transparent;
    color: var(--iam-accent);
    cursor: pointer;
}

.dialog-body label {
    display: grid;
    gap: 8px;
    margin-top: 16px;
}

.dialog-body input {
    width: 100%;
    padding: 10px 12px;
    border: 1px solid var(--iam-border);
    border-radius: 12px;
}

.dialog-actions {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    margin-top: 24px;
}

.dialog-secondary,
.dialog-primary {
    padding: 10px 14px;
    border-radius: 12px;
    cursor: pointer;
}

.dialog-secondary {
    border: 1px solid var(--iam-border);
    background: transparent;
}

.dialog-primary {
    border: 0;
    background: #12343b;
    color: #fff;
}
</style>
