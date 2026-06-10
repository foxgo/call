import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { describe, expect, it, vi } from 'vitest';

vi.mock('../api/tenant', () => ({
    tenantApi: {
        list: vi.fn().mockResolvedValue([])
    }
}));

vi.mock('../api/user', () => ({
    userApi: {
        list: vi.fn().mockResolvedValue([])
    }
}));

vi.mock('../api/role', () => ({
    roleApi: {
        list: vi.fn().mockResolvedValue([])
    }
}));

vi.mock('../api/audit', () => ({
    auditApi: {
        list: vi.fn().mockResolvedValue([])
    }
}));

import App from '../App.vue';
import { createAppRouter } from '../router';
import { useAuthStore } from '../stores/auth';

describe('App smoke', () => {
    it('renders login page by default', async () => {
        const pinia = createPinia();
        setActivePinia(pinia);
        const router = createAppRouter();

        await router.push('/');
        await router.isReady();

        const wrapper = mount(App, {
            global: {
                plugins: [pinia, router]
            }
        });

        expect(wrapper.get('[data-testid="login-hero"]').text()).toContain('统一身份与租户治理');
    });

    it('renders the authenticated shell navigation', async () => {
        const pinia = createPinia();
        setActivePinia(pinia);
        const router = createAppRouter();
        const authStore = useAuthStore();

        authStore.setSession({
            accessToken: 'access-token',
            refreshToken: 'refresh-token',
            profile: {
                userId: 1001,
                displayName: 'Tenant Admin'
            }
        });

        await router.push('/dashboard');
        await router.isReady();

        const wrapper = mount(App, {
            global: {
                plugins: [pinia, router]
            }
        });

        expect(wrapper.get('[data-testid="shell-nav"]').text()).toContain('仪表盘');
        expect(wrapper.get('[data-testid="shell-nav"]').text()).toContain('租户管理');
    });
});
