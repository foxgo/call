import { mount } from '@vue/test-utils';
import { createPinia } from 'pinia';
import { describe, expect, it } from 'vitest';

import App from '../App.vue';
import router from '../router';

describe('App smoke', () => {
    it('renders login page by default', async () => {
        await router.push('/');
        await router.isReady();

        const wrapper = mount(App, {
            global: {
                plugins: [createPinia(), router]
            }
        });

        expect(wrapper.text()).toContain('登录');
    });
});
