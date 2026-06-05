import {
    createMemoryHistory,
    createRouter,
    createWebHistory,
    type Router,
    type RouterHistory
} from 'vue-router';

import ConsoleLayout from '../layouts/ConsoleLayout.vue';
import AuditLogView from '../views/audit/AuditLogView.vue';
import DashboardView from '../views/DashboardView.vue';
import DepartmentTreeView from '../views/department/DepartmentTreeView.vue';
import LoginView from '../views/LoginView.vue';
import RoleListView from '../views/role/RoleListView.vue';
import TenantListView from '../views/tenant/TenantListView.vue';
import UserListView from '../views/user/UserListView.vue';
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
                    },
                    {
                        path: '/tenants',
                        name: 'tenants',
                        component: TenantListView
                    },
                    {
                        path: '/users',
                        name: 'users',
                        component: UserListView
                    },
                    {
                        path: '/roles',
                        name: 'roles',
                        component: RoleListView
                    },
                    {
                        path: '/departments',
                        name: 'departments',
                        component: DepartmentTreeView
                    },
                    {
                        path: '/audits',
                        name: 'audits',
                        component: AuditLogView
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
