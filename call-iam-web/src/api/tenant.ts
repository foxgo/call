import http, { unwrap } from './http';
import type { Tenant, TenantForm } from './types';

export const tenantApi = {
    async list() {
        return unwrap<Tenant[]>(http.get('/tenants'));
    },
    async get(tenantId: number) {
        return unwrap<Tenant>(http.get(`/tenants/${tenantId}`));
    },
    async create(payload: TenantForm) {
        return unwrap<Tenant>(http.post('/tenants', payload));
    },
    async update(tenantId: number, payload: Omit<TenantForm, 'tenantCode'>) {
        return unwrap<Tenant>(http.put(`/tenants/${tenantId}`, payload));
    },
    async remove(tenantId: number) {
        return unwrap<Tenant>(http.delete(`/tenants/${tenantId}`));
    }
};
