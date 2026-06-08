import http, { unwrap } from './http';
import type { CurrentUserProfile, LoginPayload, TokenPair } from './types';

export const authApi = {
    async login(payload: LoginPayload) {
        return unwrap<TokenPair>(http.post('/auth/login', payload));
    },
    async refresh(refreshToken: string) {
        return unwrap<TokenPair>(http.post('/auth/refresh', {
            refreshToken
        }));
    },
    async me() {
        return unwrap<CurrentUserProfile>(http.get('/users/me'));
    }
};
