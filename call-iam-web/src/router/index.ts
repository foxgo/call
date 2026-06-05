import {
    createMemoryHistory,
    createRouter,
    createWebHistory,
    type Router,
    type RouterHistory
} from 'vue-router';

import ConsoleLayout from '../layouts/ConsoleLayout.vue';
import DashboardView from '../views/DashboardView.vue';
import LoginView from '../views/LoginView.vue';
import { useAuthStore } from '../stores/auth';

function installRouteGuard(router: Router) {
    router.beforeEach((to) => {
        const authStore = useAuthStore();

        if (to.meta.requiresAuth && !authStore.isAuthenticated) {
            return '/login';
        }
        return true;
    });
}

export function createAppRouter(history: RouterHistory = createMemoryHistory()) {
    const router = createRouter({
        history,
        routes: [
            {
                path: '/',
                redirect: '/login'
            },
            {
                path: '/login',
                name: 'login',
                component: LoginView
            },
            {
                path: '/dashboard',
                component: ConsoleLayout,
                meta: {
                    requiresAuth: true
                },
                children: [
                    {
                        path: '',
                        name: 'dashboard',
                        component: DashboardView
                    }
                ]
            }
        ]
    });

    installRouteGuard(router);
    return router;
}

const router = createAppRouter(createWebHistory());
export default router;
