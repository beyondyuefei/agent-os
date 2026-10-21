package com.kuoge.agentos.infrastructure.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 飞书开放平台审批 API 客户端。
 *
 * <p>文档参考：https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/approval-v4/
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuApprovalClient {

    private static final String BASE_URL = "https://open.feishu.cn/open-apis";

    private final FeishuApprovalProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String tenantAccessToken;
    private volatile long tokenExpireAt;

    public String createApprovalInstance(String taskId, String taskDescription) {
        if (properties.isMockMode()) {
            return "mock-instance-" + taskId;
        }

        String token = ensureAccessToken();
        String url = BASE_URL + "/approval/v4/instances";

        Map<String, Object> body = Map.of(
                "approval_code", properties.getApprovalCode(),
                "user_id", properties.getApproverOpenId(),
                "user_id_type", "open_id",
                "form", List.of(
                        Map.of("name", "任务ID", "type", "text", "value", taskId),
                        Map.of("name", "任务描述", "type", "text", "value", taskDescription)
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                JsonNode node = objectMapper.readTree(response.getBody());
                return node.path("data").path("instance_code").asText();
            } catch (Exception e) {
                log.error("Failed to parse feishu create instance response", e);
                throw new FeishuApprovalException("Parse response failed: " + response.getBody());
            }
        }
        throw new FeishuApprovalException("Create feishu approval instance failed: " + response);
    }

    public FeishuApprovalStatus queryApprovalStatus(String instanceCode) {
        if (properties.isMockMode()) {
            return FeishuApprovalStatus.PENDING;
        }

        String token = ensureAccessToken();
        String url = BASE_URL + "/approval/v4/instances/" + instanceCode;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                JsonNode node = objectMapper.readTree(response.getBody());
                String status = node.path("data").path("status").asText("PENDING");
                return FeishuApprovalStatus.fromCode(status);
            } catch (Exception e) {
                log.error("Failed to parse feishu query response", e);
                return FeishuApprovalStatus.PENDING;
            }
        }
        return FeishuApprovalStatus.PENDING;
    }

    private synchronized String ensureAccessToken() {
        if (tenantAccessToken != null && System.currentTimeMillis() < tokenExpireAt - 60_000) {
            return tenantAccessToken;
        }

        String url = BASE_URL + "/auth/v3/tenant_access_token/internal";
        Map<String, String> body = Map.of(
                "app_id", properties.getAppId(),
                "app_secret", properties.getAppSecret()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                JsonNode node = objectMapper.readTree(response.getBody());
                tenantAccessToken = node.path("tenant_access_token").asText();
                int expire = node.path("expire").asInt(7200);
                tokenExpireAt = System.currentTimeMillis() + expire * 1000L;
                return tenantAccessToken;
            } catch (Exception e) {
                throw new FeishuApprovalException("Failed to parse access token response", e);
            }
        }
        throw new FeishuApprovalException("Failed to get feishu access token: " + response);
    }

    public enum FeishuApprovalStatus {
        PENDING,
        APPROVED,
        REJECTED,
        CANCELED;

        public static FeishuApprovalStatus fromCode(String code) {
            return switch (code.toUpperCase()) {
                case "APPROVED" -> APPROVED;
                case "REJECTED" -> REJECTED;
                case "CANCELED" -> CANCELED;
                default -> PENDING;
            };
        }
    }

    public static class FeishuApprovalException extends RuntimeException {
        public FeishuApprovalException(String message) {
            super(message);
        }

        public FeishuApprovalException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
