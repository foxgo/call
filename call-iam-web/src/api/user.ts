import http, { unwrap } from './http';
import type { CurrentUserProfile, User, UserForm, UserUpdateForm } from './types';

export const userApi = {
    async me() {
        return unwrap<CurrentUserProfile>(http.get('/users/me'));
    },
    async list(filters: {
        departmentId?: number;
    } = {}) {
        return unwrap<User[]>(http.get('/users', {
            params: filters
        }));
    },
    async get(userId: number) {
        return unwrap<User>(http.get(`/users/${userId}`));
    },
    async create(payload: UserForm) {
        return unwrap<User>(http.post('/users', payload));
    },
    async update(userId: number, payload: UserUpdateForm) {
        return unwrap<User>(http.put(`/users/${userId}`, payload));
    },
    async remove(userId: number) {
        return unwrap<User>(http.delete(`/users/${userId}`));
    },
    async updateStatus(userId: number, status: string) {
        return unwrap<User>(http.put(`/users/${userId}/status`, {
            status
        }));
    },
    async resetPassword(userId: number, newPassword: string) {
        return unwrap<User>(http.put(`/users/${userId}/password/reset`, {
            newPassword
        }));
    },
    async assignRoles(userId: number, roleIds: number[]) {
        return unwrap<User>(http.put(`/users/${userId}/roles`, {
            roleIds
        }));
    },
    async assignDepartments(userId: number, departmentIds: number[]) {
        return unwrap<User>(http.put(`/users/${userId}/departments`, {
            departmentIds
        }));
    }
};
