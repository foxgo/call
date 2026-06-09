package com.callcenter.persistence.context;

public final class DbRouteContextHolder {

    private static final ThreadLocal<Integer> HOLDER = new ThreadLocal<>();

    private DbRouteContextHolder() {
    }

    public static void set(Integer dbIndex) {
        HOLDER.set(dbIndex);
    }

    public static Integer get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
