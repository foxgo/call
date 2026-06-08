import http, { unwrap } from './http';
import type { AuditLog, AuditLogFilters } from './types';

export const auditApi = {
    async list(filters: AuditLogFilters = {}) {
        return unwrap<AuditLog[]>(http.get('/audit-logs', {
            params: filters
        }));
    },
    async get(auditLogId: number) {
        return unwrap<AuditLog>(http.get(`/audit-logs/${auditLogId}`));
    }
};
