package com.kuoge.agentos.channel.mock;

import com.kuoge.agentos.channel.AdChannelAdapter;
import com.kuoge.agentos.channel.AdCreateReceipt;
import com.kuoge.agentos.channel.AdMapping;
import com.kuoge.agentos.channel.ChannelCapabilityProfile;
import com.kuoge.agentos.channel.CreationStatus;
import com.kuoge.agentos.channel.InsightReport;
import com.kuoge.agentos.channel.Window;
import com.kuoge.agentos.delivery.ApprovedItemSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Meta 广告通道 Mock 实现（一期交付）：
 * 不是简单返回成功，而是仿真真实平台的关键边界，支撑规则层验收：
 * <ul>
 *   <li>可配置注入 UNKNOWN 创建结果 → 演练"待查 → 反查"路径（验收规则 5）</li>
 *   <li>记录平台侧提交次数 → 幂等断言（系统边界只提交 1 次）</li>
 *   <li>确定性伪随机生成观察数据（seed 固定，测试可重现）</li>
 *   <li>可注入跨品订单（"A 广告买 B"）→ 归因拆分断言（验收规则 7）</li>
 *   <li>维护平台侧已创建对象与花费 → 预算双账对账（验收规则 6）</li>
 * </ul>
 */
public class MockAdChannelAdapter implements AdChannelAdapter {

    /** 平台侧已创建广告：platformAdId → 快照 */
    private final Map<String, ApprovedItemSnapshot> createdAds = new ConcurrentHashMap<>();
    /** 请求 → 创建结果（反查依据） */
    private final Map<String, CreationStatus> requestOutcomes = new ConcurrentHashMap<>();
    /** 请求 → 平台广告 ID */
    private final Map<String, String> requestAdIds = new ConcurrentHashMap<>();
    /** 平台侧提交计数（幂等验收：同快照并发触发，平台只应收到 1 次） */
    private final AtomicInteger platformSubmissions = new AtomicInteger(0);
    /** 平台侧启停动作计数（断言启停指令真实触达平台） */
    private final AtomicInteger enableCalls = new AtomicInteger(0);
    private final AtomicInteger pauseCalls = new AtomicInteger(0);
    private final AtomicInteger endCalls = new AtomicInteger(0);
    /** 平台侧花费（对账数据源）：platformAdId → spendCents */
    private final Map<String, Long> platformSpent = new ConcurrentHashMap<>();
    /** 注入的跨品订单：platformAdId → orders */
    private final Map<String, List<InsightReport.AttributedOrder>> injectedOrders = new ConcurrentHashMap<>();

    private final AtomicInteger sequence = new AtomicInteger(0);
    /** 仿真开关：下一次 createAd 返回 UNKNOWN */
    private volatile boolean nextCreateUnknown = false;
    /** 仿真开关：平台对象映射粒度 */
    private final ChannelCapabilityProfile profile = new ChannelCapabilityProfile(
            "MOCK_META",
            List.of("campaign", "adset", "ad"),
            true,
            false
    );

    @Override
    public ChannelCapabilityProfile profile() {
        return profile;
    }

    @Override
    public AdCreateReceipt createAd(ApprovedItemSnapshot snapshot) {
        platformSubmissions.incrementAndGet();
        String requestId = "mock_req_" + sequence.incrementAndGet();
        if (nextCreateUnknown) {
            nextCreateUnknown = false;
            // 平台实际已创建，但响应丢失 → 反查能查到
            String adId = "mock_ad_" + sequence.incrementAndGet();
            createdAds.put(adId, snapshot);
            requestOutcomes.put(requestId, CreationStatus.CREATED);
            requestAdIds.put(requestId, adId);
            return AdCreateReceipt.unknown(requestId, Map.of("simulated", "response_lost"));
        }
        String adId = "mock_ad_" + sequence.incrementAndGet();
        createdAds.put(adId, snapshot);
        requestOutcomes.put(requestId, CreationStatus.CREATED);
        requestAdIds.put(requestId, adId);
        return AdCreateReceipt.success(requestId, adId,
                Map.of("channel", "MOCK_META", "productId", snapshot.productId()));
    }

    @Override
    public CreationStatus getCreationStatus(String platformRequestId) {
        return requestOutcomes.getOrDefault(platformRequestId, CreationStatus.STILL_UNKNOWN);
    }

    /** 反查确认已创建时取回平台广告 ID */
    public String resolveAdId(String platformRequestId) {
        return requestAdIds.get(platformRequestId);
    }

    @Override
    public String resolvePlatformAdId(String platformRequestId) {
        return requestAdIds.get(platformRequestId);
    }

    @Override
    public void enable(AdMapping mapping) {
        requireCreated(mapping.platformAdId());
        enableCalls.incrementAndGet();
    }

    @Override
    public void pause(AdMapping mapping) {
        requireCreated(mapping.platformAdId());
        pauseCalls.incrementAndGet();
    }

    @Override
    public void end(AdMapping mapping) {
        requireCreated(mapping.platformAdId());
        endCalls.incrementAndGet();
    }

    private void requireCreated(String platformAdId) {
        if (platformAdId == null || !createdAds.containsKey(platformAdId)) {
            throw new IllegalStateException("PLATFORM_OBJECT_NOT_FOUND: " + platformAdId);
        }
    }

    @Override
    public InsightReport fetchInsights(AdMapping mapping, Window window) {
        requireCreated(mapping.platformAdId());
        // 确定性伪随机：以 adId 哈希为 seed，测试可重现
        int seed = Math.abs(mapping.platformAdId().hashCode());
        long spend = 1000L + seed % 5000;
        long impressions = 2000L + seed % 20000;
        long clicks = impressions / (10 + seed % 40);
        platformSpent.merge(mapping.platformAdId(), spend, Long::sum);
        List<InsightReport.AttributedOrder> orders =
                new ArrayList<>(injectedOrders.getOrDefault(mapping.platformAdId(), List.of()));
        return new InsightReport(mapping.platformAdId(), window, spend, impressions, clicks, orders);
    }

    // ---- 仿真控制（测试用） ----

    /** 下一次 createAd 返回 UNKNOWN（平台实际已创建） */
    public void simulateNextCreateUnknown() {
        this.nextCreateUnknown = true;
    }

    /** 注入跨品订单（"A 广告买 B" 用例） */
    public void injectOrder(String platformAdId, String orderId, String purchasedProductId, long amountCents) {
        injectedOrders.computeIfAbsent(platformAdId, k -> new ArrayList<>())
                .add(new InsightReport.AttributedOrder(orderId, purchasedProductId, amountCents));
    }

    /** 平台侧累计提交次数（幂等验收口径：系统边界） */
    public int platformSubmissionCount() {
        return platformSubmissions.get();
    }

    /** 平台侧累计 enable 调用次数 */
    public int enableCallCount() {
        return enableCalls.get();
    }

    /** 平台侧累计 pause 调用次数 */
    public int pauseCallCount() {
        return pauseCalls.get();
    }

    /** 平台侧累计 end 调用次数 */
    public int endCallCount() {
        return endCalls.get();
    }

    /** 平台侧累计花费（对账数据源） */
    public long platformSpent(String platformAdId) {
        return platformSpent.getOrDefault(platformAdId, 0L);
    }

    public boolean adExists(String platformAdId) {
        return createdAds.containsKey(platformAdId);
    }
}
