import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import DepartmentTreeView from '../views/department/DepartmentTreeView.vue';
import TenantListView from '../views/tenant/TenantListView.vue';
import UserListView from '../views/user/UserListView.vue';

describe('management pages', () => {
    it('loads tenant rows', () => {
        const wrapper = mount(TenantListView);

        expect(wrapper.text()).toContain('Acme Cloud');
        expect(wrapper.text()).toContain('Northwind Contact Center');
    });

    it('opens create user dialog', async () => {
        const wrapper = mount(UserListView);

        await wrapper.get('[data-testid="open-create-user"]').trigger('click');

        expect(wrapper.text()).toContain('新建用户');
    });

    it('renders department tree data', () => {
        const wrapper = mount(DepartmentTreeView);

        expect(wrapper.text()).toContain('平台运营中心');
        expect(wrapper.text()).toContain('华东客服一部');
    });
});
