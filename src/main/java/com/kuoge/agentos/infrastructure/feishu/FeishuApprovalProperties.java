package com.kuoge.agentos.infrastructure.feishu;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agentos.feishu.approval")
public class FeishuApprovalProperties {

    /** 是否启用 Mock 模式（自动审批，用于开发和测试） */
    private boolean mockMode = true;

    /** 飞书应用 ID */
    private String appId;

    /** 飞书应用 Secret */
    private String appSecret;

    /** 审批定义 Code */
    private String approvalCode;

    /** 审批发起人的 user_id / open_id */
    private String approverOpenId;

    /** 轮询间隔（毫秒） */
    private long pollIntervalMs = 5000;

    /** 最大轮询次数 */
    private int maxPollTimes = 60;
}
