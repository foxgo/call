import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';

import http from '../api/http';
import HeaderBar from '../components/HeaderBar.vue';
import LoginView from '../views/LoginView.vue';
import { createAppRouter } from '../router';
import { initializeAuth, useAuthStore } from '../stores/auth';

describe('auth flow', () => {
    beforeEach(() => {
        setActivePinia(createPinia());
        vi.restoreAllMocks();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('stores tokens and profile after successful login', async () => {
        vi.spyOn(http, 'post').mockResolvedValue({
            data: {
                success: true,
                data: {
                    accessToken: 'access-token',
                    refreshToken: 'refresh-token'
                }
            }
        });
        vi.spyOn(http, 'get').mockResolvedValue({
            data: {
                success: true,
                data: {
                    userId: 1001,
                    displayName: 'Tenant Admin',
                    tenantId: 9,
                    roleIds: [11],
                    departmentIds: [20]
                }
            }
        });

        const router = createAppRouter();
        const authStore = useAuthStore();

        initializeAuth(router);
        await authStore.login({
            tenantCode: 'acme',
            account: 'tenant-admin',
            password: 'secret'
        });

        expect(authStore.accessToken).toBe('access-token');
        expect(authStore.refreshToken).toBe('refresh-token');
        expect(authStore.profile).toEqual({
            userId: 1001,
            displayName: 'Tenant Admin',
            tenantId: 9,
            roleIds: [11],
            departmentIds: [20]
        });
    });

    it('redirects to login and clears session on unauthorized response', async () => {
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

        initializeAuth(router);

        const interceptor = (http.interceptors.response as any).handlers.at(-1);
        await interceptor.rejected({
            response: {
                status: 401
            }
        }).catch(() => undefined);

        expect(authStore.isAuthenticated).toBe(false);
        expect(router.currentRoute.value.fullPath).toBe('/login');
    });

    it('redirects protected routes to login when session is missing', async () => {
        const router = createAppRouter();

        await router.push('/dashboard');
        await router.isReady();

        expect(router.currentRoute.value.fullPath).toBe('/login');
    });

    it('submits the login form and navigates to dashboard', async () => {
        vi.spyOn(http, 'post').mockResolvedValue({
            data: {
                success: true,
                data: {
                    accessToken: 'access-token',
                    refreshToken: 'refresh-token'
                }
            }
        });
        vi.spyOn(http, 'get').mockResolvedValue({
            data: {
                success: true,
                data: {
                    userId: 1001,
                    displayName: 'Tenant Admin',
                    tenantId: 9,
                    roleIds: [11],
                    departmentIds: [20]
                }
            }
        });

        const pinia = createPinia();
        setActivePinia(pinia);
        const router = createAppRouter();
        initializeAuth(router);

        await router.push('/login');
        await router.isReady();

        const wrapper = mount(LoginView, {
            global: {
                plugins: [pinia, router, ElementPlus]
            }
        });

        expect(wrapper.get('[data-testid="login-hero"]').text()).toContain('统一身份与租户治理');
        await wrapper.get('[data-testid="tenant-code-input"]').setValue('acme');
        await wrapper.get('[data-testid="account-input"]').setValue('tenant-admin');
        await wrapper.get('[data-testid="password-input"]').setValue('secret');
        await wrapper.get('[data-testid="login-submit"]').trigger('submit');
        await flushPromises();

        expect(router.currentRoute.value.fullPath).toBe('/dashboard');
    });

    it('renders current user in the header', () => {
        const pinia = createPinia();
        setActivePinia(pinia);
        const authStore = useAuthStore();

        authStore.setSession({
            accessToken: 'access-token',
            refreshToken: 'refresh-token',
            profile: {
                userId: 1001,
                displayName: 'Tenant Admin',
                tenantId: 9,
                roleIds: [11],
                departmentIds: [20]
            }
        });

        const wrapper = mount(HeaderBar, {
            global: {
                plugins: [pinia, createAppRouter()]
            }
        });

        expect(wrapper.text()).toContain('Tenant Admin');
        expect(wrapper.get('[data-testid="header-user-chip"]').text()).toContain('Tenant Admin');
    });
});
