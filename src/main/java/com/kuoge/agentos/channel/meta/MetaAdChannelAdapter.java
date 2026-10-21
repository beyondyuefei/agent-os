package com.kuoge.agentos.channel.meta;

import com.kuoge.agentos.channel.AdChannelAdapter;
import com.kuoge.agentos.channel.AdCreateReceipt;
import com.kuoge.agentos.channel.AdMapping;
import com.kuoge.agentos.channel.ChannelCapabilityProfile;
import com.kuoge.agentos.channel.CreationStatus;
import com.kuoge.agentos.channel.InsightReport;
import com.kuoge.agentos.channel.Window;
import com.kuoge.agentos.delivery.ApprovedItemSnapshot;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Meta Marketing API 真实适配器（二期接入）。
 * 结构与 Mock 共用 AdChannelAdapter 契约；上线前须以沙盒账号跑通
 * AdChannelAdapterContractTest 全部用例，并验证平台侧自动优化可关闭、可审计。
 *
 * 对象映射：单图投放项 → Campaign / AdSet / Ad 结构在接入时确定，
 * 技术可合并请求，业务状态与回执仍按单图保留。
 */
public class MetaAdChannelAdapter implements AdChannelAdapter {

    private final MetaProperties properties;
    private final RestClient http;

    public MetaAdChannelAdapter(MetaProperties properties) {
        this.properties = properties;
        this.http = RestClient.builder()
                .baseUrl(properties.getBaseUrl() + "/" + properties.getApiVersion())
                .defaultHeader("Authorization", "Bearer " + properties.getAccessToken())
                .build();
    }

    @Override
    public ChannelCapabilityProfile profile() {
        return new ChannelCapabilityProfile(
                "META",
                List.of("campaign", "adset", "ad"),
                // Advantage+ 类平台侧自动优化：接入时逐项验收，关不掉则不接该套餐
                true,
                // 平台原生幂等能力未验证前，不承诺平台侧去重（验收规则 5 以系统边界为准）
                false
        );
    }

    @Override
    public AdCreateReceipt createAd(ApprovedItemSnapshot snapshot) {
        // TODO(二期): POST /act_{adAccountId}/campaigns → /adsets → /ads
        // 业务状态与回执按单图保留；超时 / 无响应 → UNKNOWN，由上层进入反查，不得自动重提
        Map<String, Object> body = http.post()
                .uri("/act_" + properties.getAdAccountId() + "/ads")
                .body(Map.of(
                        "name", "agent-os-" + snapshot.deliveryItemId(),
                        "status", "PAUSED"))
                .retrieve()
                .body(Map.class);
        String adId = body == null ? null : (String) body.get("id");
        String requestId = body == null ? null : (String) body.get("request_id");
        if (adId == null) {
            return AdCreateReceipt.unknown(requestId, Map.of("raw", String.valueOf(body)));
        }
        return AdCreateReceipt.success(requestId, adId, body);
    }

    @Override
    public CreationStatus getCreationStatus(String platformRequestId) {
        // TODO(二期): 按 requestId 反查原请求结果；确认未创建才允许上层重试
        throw new UnsupportedOperationException("Meta creation-status recheck not implemented yet");
    }

    @Override
    public void enable(AdMapping mapping) {
        updateStatus(mapping.platformAdId(), "ACTIVE");
    }

    @Override
    public void pause(AdMapping mapping) {
        updateStatus(mapping.platformAdId(), "PAUSED");
    }

    @Override
    public void end(AdMapping mapping) {
        updateStatus(mapping.platformAdId(), "ARCHIVED");
    }

    private void updateStatus(String platformAdId, String status) {
        http.post()
                .uri("/" + platformAdId)
                .body(Map.of("status", status))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public InsightReport fetchInsights(AdMapping mapping, Window window) {
        // TODO(二期): GET /{ad-id}/insights?time_range=... 字段映射为 InsightReport
        throw new UnsupportedOperationException("Meta insights fetch not implemented yet");
    }
}
