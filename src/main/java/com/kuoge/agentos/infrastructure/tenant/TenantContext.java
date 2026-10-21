package com.kuoge.agentos.infrastructure.tenant;

/**
 * 多租户上下文。
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String get() {
        String tenantId = CURRENT_TENANT.get();
        return tenantId == null ? "default" : tenantId;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
