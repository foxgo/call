package com.callcenter.common.context;

import com.callcenter.common.route.ShardContext;

public final class ShardContextHolder {

    private static final ThreadLocal<ShardContext> HOLDER = new ThreadLocal<>();

    private ShardContextHolder() {
    }

    public static void set(ShardContext context) {
        HOLDER.set(context);
        DbRouteContextHolder.set(context.dbIndex());
    }

    public static ShardContext get() {
        return HOLDER.get();
    }

    public static ShardContext getRequired() {
        ShardContext context = HOLDER.get();
        if (context == null) {
            throw new IllegalStateException("Shard context not set");
        }
        return context;
    }

    public static void clear() {
        HOLDER.remove();
        DbRouteContextHolder.clear();
    }
}

