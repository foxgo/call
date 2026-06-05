import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import http from '../api/http';
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
                    displayName: 'Tenant Admin'
                }
            }
        });

        const router = createAppRouter();
        const authStore = useAuthStore();

        initializeAuth(router);
        await authStore.login({
            account: 'tenant-admin',
            password: 'secret'
        });

        expect(authStore.accessToken).toBe('access-token');
        expect(authStore.refreshToken).toBe('refresh-token');
        expect(authStore.profile).toEqual({
            userId: 1001,
            displayName: 'Tenant Admin'
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
});
