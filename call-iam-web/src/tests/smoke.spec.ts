import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { describe, expect, it } from 'vitest';

import App from '../App.vue';
import router from '../router';

describe('App smoke', () => {
    it('renders login page by default', async () => {
        const pinia = createPinia();
        setActivePinia(pinia);

        await router.push('/');
        await router.isReady();

        const wrapper = mount(App, {
            global: {
                plugins: [pinia, router]
            }
        });

        expect(wrapper.text()).toContain('登录');
    });
});
