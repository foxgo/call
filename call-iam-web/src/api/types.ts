export type ApiEnvelope<T> = {
    success: boolean;
    data: T;
    errorCode?: string | null;
    message?: string | null;
};

export type LoginPayload = {
    tenantCode?: string;
    account: string;
    password: string;
};

export type TokenPair = {
    accessToken: string;
    refreshToken: string;
};

export type CurrentUserProfile = {
    userId: number;
    displayName: string;
    tenantId: number | null;
    roleIds: number[];
    departmentIds: number[];
};

export type Tenant = {
    id: number;
    tenantCode: string;
    tenantName: string;
    status: string;
    expireTime: string | null;
};

export type TenantForm = {
    tenantCode: string;
    tenantName: string;
    expireTime: string | null;
};

export type User = {
    id: number;
    username: string;
    mobile: string | null;
    email: string | null;
    nickname: string;
    status: string;
    roleIds: number[];
    departmentIds: number[];
};

export type UserForm = {
    username: string;
    mobile: string;
    email: string;
    password: string;
    nickname: string;
};

export type UserUpdateForm = Omit<UserForm, 'username' | 'password'>;

export type Role = {
    id: number;
    roleCode: string;
    roleName: string;
    roleType: string;
    permissionIds: number[];
    dataScope: RoleDataScope | null;
};

export type RoleForm = {
    roleCode: string;
    roleName: string;
    roleType: string;
};

export type RoleDataScope = {
    scopeType: string;
    departmentId: number | null;
};

export type Permission = {
    id: number;
    permissionCode: string;
    permissionName: string;
};

export type Department = {
    id: number;
    parentId: number | null;
    name: string;
    status: string;
    sort: number;
};

export type DepartmentTreeNode = Department & {
    children: DepartmentTreeNode[];
};

export type DepartmentForm = {
    parentId: number | null;
    name: string;
    status: string;
    sort: number;
};

export type AuditLog = {
    id: number;
    tenantId: number;
    operatorId: number | null;
    action: string;
    resourceType: string;
    resourceId: string | null;
    createdAt: string;
};

export type AuditLogFilters = {
    operatorId?: number;
    resourceType?: string;
    resourceId?: string;
};
