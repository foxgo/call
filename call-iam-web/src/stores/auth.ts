import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import type { Router } from 'vue-router';

import http from '../api/http';

export type UserProfile = {
    userId: number;
    displayName: string;
};

type LoginCommand = {
    account: string;
    password: string;
};

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

    async function login(command: LoginCommand) {
        const loginResponse = await http.post('/auth/login', {
            account: command.account,
            password: command.password
        });
        const tokens = loginResponse.data.data;

        accessToken.value = tokens.accessToken;
        refreshToken.value = tokens.refreshToken;

        const profileResponse = await http.get('/users/me');
        profile.value = profileResponse.data.data;
    }

    return {
        accessToken,
        refreshToken,
        profile,
        isAuthenticated,
        login,
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
