package com.kuoge.agentos.business.service.crossborder;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 跨境电商自建站管理后台接入配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "agentos.crossborder")
public class CrossBorderProperties {

    /** 管理后台地址，默认测试环境 */
    private String baseUrl = "https://test.admin.CrossBorder.com";

    /** 登录邮箱 */
    private String email;

    /** 登录密码 */
    private String password;

    /** 默认租户 ID */
    private String tenantId = "1";

    /** 默认站点数据源（self_site / shopify / all） */
    private String site = "self_site";

    /** 是否启用真实 API（false 时服务可自行降级） */
    private boolean enabled = true;

    public String resolveTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank() || "default".equals(tenantId)) {
            return this.tenantId;
        }
        if (tenantId.startsWith("tenant-")) {
            return tenantId.substring("tenant-".length());
        }
        return tenantId;
    }
}
