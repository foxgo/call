import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
    plugins: [vue()],
    server: {
        proxy: {
            '/api/iam': {
                target: 'http://127.0.0.1:8085',
                changeOrigin: true
            }
        }
    },
    test: {
        environment: 'jsdom'
    }
});
