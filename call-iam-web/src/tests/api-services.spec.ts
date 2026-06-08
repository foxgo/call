import { beforeEach, describe, expect, it, vi } from 'vitest';

import http from '../api/http';
import { auditApi } from '../api/audit';
import { authApi } from '../api/auth';
import { departmentApi } from '../api/department';
import { roleApi } from '../api/role';
import { tenantApi } from '../api/tenant';
import { userApi } from '../api/user';

describe('api services', () => {
    beforeEach(() => {
        vi.restoreAllMocks();
    });

    it('unwraps login payload', async () => {
        vi.spyOn(http, 'post').mockResolvedValue({
            data: {
                success: true,
                data: {
                    accessToken: 'access-token',
                    refreshToken: 'refresh-token'
                }
            }
        });

        await expect(authApi.login({
            tenantCode: 'acme',
            account: 'tenant-admin',
            password: 'Abcdef12'
        })).resolves.toEqual({
            accessToken: 'access-token',
            refreshToken: 'refresh-token'
        });
        expect(http.post).toHaveBeenCalledWith('/auth/login', {
            tenantCode: 'acme',
            account: 'tenant-admin',
            password: 'Abcdef12'
        });
    });

    it('passes optional query params for users', async () => {
        vi.spyOn(http, 'get').mockResolvedValue({
            data: {
                success: true,
                data: []
            }
        });

        await userApi.list({
            departmentId: 20
        });

        expect(http.get).toHaveBeenCalledWith('/users', {
            params: {
                departmentId: 20
            }
        });
    });

    it('posts tenant create payload', async () => {
        vi.spyOn(http, 'post').mockResolvedValue({
            data: {
                success: true,
                data: {
                    id: 9,
                    tenantCode: 'acme',
                    tenantName: 'Acme',
                    status: 'ACTIVE',
                    expireTime: '2027-01-01T00:00:00'
                }
            }
        });

        await tenantApi.create({
            tenantCode: 'acme',
            tenantName: 'Acme',
            expireTime: '2027-01-01T00:00:00'
        });

        expect(http.post).toHaveBeenCalledWith('/tenants', {
            tenantCode: 'acme',
            tenantName: 'Acme',
            expireTime: '2027-01-01T00:00:00'
        });
    });

    it('updates role permissions via dedicated endpoint', async () => {
        vi.spyOn(http, 'put').mockResolvedValue({
            data: {
                success: true,
                data: {
                    id: 11,
                    roleCode: 'tenant-admin',
                    roleName: 'Tenant Admin',
                    roleType: 'TENANT_CUSTOM',
                    permissionIds: [101, 102],
                    dataScope: null
                }
            }
        });

        await roleApi.updatePermissions(11, [101, 102]);

        expect(http.put).toHaveBeenCalledWith('/roles/11/permissions', {
            permissionIds: [101, 102]
        });
    });

    it('loads department tree and audit detail', async () => {
        const getSpy = vi.spyOn(http, 'get')
            .mockResolvedValueOnce({
                data: {
                    success: true,
                    data: []
                }
            })
            .mockResolvedValueOnce({
                data: {
                    success: true,
                    data: {
                        id: 1
                    }
                }
            });

        await departmentApi.tree();
        await auditApi.get(1);

        expect(getSpy).toHaveBeenNthCalledWith(1, '/departments/tree');
        expect(getSpy).toHaveBeenNthCalledWith(2, '/audit-logs/1');
    });
});
