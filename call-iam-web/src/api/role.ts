import http, { unwrap } from './http';
import type { Permission, Role, RoleDataScope, RoleForm } from './types';

export const roleApi = {
    async list() {
        return unwrap<Role[]>(http.get('/roles'));
    },
    async get(roleId: number) {
        return unwrap<Role>(http.get(`/roles/${roleId}`));
    },
    async create(payload: RoleForm) {
        return unwrap<Role>(http.post('/roles', payload));
    },
    async update(roleId: number, payload: RoleForm) {
        return unwrap<Role>(http.put(`/roles/${roleId}`, payload));
    },
    async remove(roleId: number) {
        return unwrap<Role>(http.delete(`/roles/${roleId}`));
    },
    async updatePermissions(roleId: number, permissionIds: number[]) {
        return unwrap<Role>(http.put(`/roles/${roleId}/permissions`, {
            permissionIds
        }));
    },
    async updateDataScope(roleId: number, payload: RoleDataScope) {
        return unwrap<Role>(http.put(`/roles/${roleId}/data-scope`, payload));
    },
    async listPermissions() {
        return unwrap<Permission[]>(http.get('/permissions'));
    }
};
