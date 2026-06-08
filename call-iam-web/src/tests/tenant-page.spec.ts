import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

import DepartmentTreeView from '../views/department/DepartmentTreeView.vue';
import TenantListView from '../views/tenant/TenantListView.vue';
import UserListView from '../views/user/UserListView.vue';

vi.mock('../api/tenant', () => ({
    tenantApi: {
        list: vi.fn().mockResolvedValue([
            { id: 1, tenantCode: 'acme', tenantName: 'Acme Cloud', status: 'ACTIVE', expireTime: '2027-01-01T00:00:00' },
            { id: 2, tenantCode: 'northwind', tenantName: 'Northwind Contact Center', status: 'ACTIVE', expireTime: '2027-06-01T00:00:00' }
        ])
    }
}));

vi.mock('../api/user', () => ({
    userApi: {
        list: vi.fn().mockResolvedValue([
            {
                id: 1,
                username: 'linxiao',
                mobile: '13800138000',
                email: 'linxiao@example.com',
                nickname: '林霄',
                status: 'ENABLE',
                roleIds: [11],
                departmentIds: [10]
            }
        ])
    }
}));

vi.mock('../api/role', () => ({
    roleApi: {
        list: vi.fn().mockResolvedValue([
            {
                id: 11,
                roleCode: 'tenant-admin',
                roleName: 'Tenant Admin',
                roleType: 'TENANT_CUSTOM',
                permissionIds: [101],
                dataScope: null
            }
        ])
    }
}));

vi.mock('../api/department', () => ({
    departmentApi: {
        tree: vi.fn().mockResolvedValue([
            {
                id: 10,
                parentId: null,
                name: '平台运营中心',
                status: 'ACTIVE',
                sort: 0,
                children: [
                    {
                        id: 20,
                        parentId: 10,
                        name: '华东客服一部',
                        status: 'ACTIVE',
                        sort: 10,
                        children: []
                    }
                ]
            }
        ])
    }
}));

describe('management pages', () => {
    it('loads tenant rows', async () => {
        const wrapper = mount(TenantListView);
        await flushPromises();

        expect(wrapper.text()).toContain('Acme Cloud');
        expect(wrapper.text()).toContain('Northwind Contact Center');
    });

    it('opens create user dialog', async () => {
        const wrapper = mount(UserListView);
        await flushPromises();

        await wrapper.get('[data-testid="open-create-user"]').trigger('click');

        expect(wrapper.text()).toContain('新建用户');
    });

    it('renders department tree data', async () => {
        const wrapper = mount(DepartmentTreeView);
        await flushPromises();

        expect(wrapper.text()).toContain('平台运营中心');
        expect(wrapper.text()).toContain('华东客服一部');
    });
});
