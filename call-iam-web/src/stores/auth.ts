import { defineStore } from 'pinia';
import { computed, ref } from 'vue';

type UserProfile = {
    userId: number;
    displayName: string;
};

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

    return {
        accessToken,
        refreshToken,
        profile,
        isAuthenticated,
        setSession,
        clearSession
    };
});
