import http, { unwrap } from './http';
import type { Department, DepartmentForm, DepartmentTreeNode } from './types';

export const departmentApi = {
    async tree() {
        return unwrap<DepartmentTreeNode[]>(http.get('/departments/tree'));
    },
    async get(departmentId: number) {
        return unwrap<Department>(http.get(`/departments/${departmentId}`));
    },
    async create(payload: DepartmentForm) {
        return unwrap<Department>(http.post('/departments', payload));
    },
    async update(departmentId: number, payload: Omit<DepartmentForm, 'parentId'>) {
        return unwrap<Department>(http.put(`/departments/${departmentId}`, payload));
    },
    async move(departmentId: number, parentId: number | null) {
        return unwrap<Department>(http.put(`/departments/${departmentId}/move`, {
            parentId
        }));
    },
    async remove(departmentId: number) {
        return unwrap<Department>(http.delete(`/departments/${departmentId}`));
    }
};
