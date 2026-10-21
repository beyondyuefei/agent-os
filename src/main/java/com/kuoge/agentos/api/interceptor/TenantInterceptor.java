package com.kuoge.agentos.api.interceptor;

import com.kuoge.agentos.infrastructure.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * HTTP 请求租户拦截器。
 *
 * <p>从请求头 X-Tenant-Id 中解析租户 ID，绑定到线程上下文。
 */
@Component
public class TenantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String tenantId = request.getHeader("X-Tenant-Id");
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "default";
        }
        TenantContext.set(tenantId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        TenantContext.clear();
    }
}
