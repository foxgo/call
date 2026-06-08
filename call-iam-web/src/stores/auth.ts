import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import type { Router } from 'vue-router';

import http from '../api/http';
import { authApi } from '../api/auth';
import type { CurrentUserProfile, LoginPayload } from '../api/types';

export type UserProfile = CurrentUserProfile;

let responseInterceptorId: number | null = null;
let requestInterceptorId: number | null = null;

export const useAuthStore = defineStore('auth', () => {
    const accessToken = ref<string | null>(null);
    const refreshToken = ref<string | null>(null);
    const profile = ref<UserProfile | null>(null);

    const isAuthenticated = computed(() => accessToken.value !== null);

    function setSession(session: {
        accessToken: string;
        refreshToken: string;
        profile: UserProfile;
    }) {
        accessToken.value = session.accessToken;
        refreshToken.value = session.refreshToken;
        profile.value = session.profile;
    }

    function clearSession() {
        accessToken.value = null;
        refreshToken.value = null;
        profile.value = null;
    }

    async function login(command: LoginPayload) {
        const tokens = await authApi.login(command);
        accessToken.value = tokens.accessToken;
        refreshToken.value = tokens.refreshToken;
        profile.value = await authApi.me();
    }

    function logout() {
        clearSession();
    }

    return {
        accessToken,
        refreshToken,
        profile,
        isAuthenticated,
        login,
        logout,
        setSession,
        clearSession
    };
});

export function initializeAuth(router: Router) {
    const authStore = useAuthStore();

    if (requestInterceptorId !== null) {
        http.interceptors.request.eject(requestInterceptorId);
    }
    if (responseInterceptorId !== null) {
        http.interceptors.response.eject(responseInterceptorId);
    }

    requestInterceptorId = http.interceptors.request.use((config) => {
        if (authStore.accessToken) {
            config.headers = config.headers ?? {};
            config.headers.Authorization = `Bearer ${authStore.accessToken}`;
        }
        return config;
    });

    responseInterceptorId = http.interceptors.response.use(
            (response) => response,
            async (error) => {
                if (error?.response?.status === 401) {
                    authStore.clearSession();
                    await router.push('/login');
                }
                return Promise.reject(error);
            }
    );
}
