package com.kuoge.agentos.channel;

import com.kuoge.agentos.channel.mock.MockAdChannelAdapter;
import com.kuoge.agentos.delivery.ApprovedItemSnapshot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通道适配器契约测试：对 Mock 实现跑 AdChannelAdapter 接口契约，
 * 后续 Meta 线上实现（MetaAdChannelAdapter）必须满足同一组断言，保证可平滑切换。
 */
class AdChannelAdapterContractTest {

    private AdChannelAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MockAdChannelAdapter();
    }

    private ApprovedItemSnapshot snapshot() {
        return new ApprovedItemSnapshot("snap_1", "di_1", 1, 1, 1, "1.x",
                "prod_1", "img_1", "prod_1", List.of("prod_1", "prod_2"),
                100000, "RESERVED", List.of());
    }

    private AdMapping mapping(String platformAdId) {
        return new AdMapping("map_1", "di_1", "tenant_1", platformAdId, null, null);
    }

    @Test
    void profileIsDeclared() {
        ChannelCapabilityProfile profile = adapter.profile();
        assertNotNull(profile);
        assertNotNull(profile.channel());
        assertFalse(profile.objectLevels().isEmpty(), "必须声明平台对象映射粒度");
    }

    @Test
    void createAdReturnsStructuredReceipt() {
        AdCreateReceipt receipt = adapter.createAd(snapshot());
        assertEquals(AdCreateReceipt.Status.SUCCESS, receipt.status());
        assertNotNull(receipt.platformRequestId());
        assertNotNull(receipt.platformAdId());
    }

    @Test
    void opsOnNonExistingObjectRejected() {
        AdMapping ghost = mapping("ad_not_exist");
        assertThrows(IllegalStateException.class, () -> adapter.enable(ghost));
        assertThrows(IllegalStateException.class, () -> adapter.pause(ghost));
        assertThrows(IllegalStateException.class, () -> adapter.end(ghost));
    }

    @Test
    void insightsAreDeterministicAndReplayable() {
        AdCreateReceipt receipt = adapter.createAd(snapshot());
        AdMapping mapping = mapping(receipt.platformAdId());
        Window window = new Window(LocalDate.now().minusDays(1), LocalDate.now());

        InsightReport first = adapter.fetchInsights(mapping, window);
        InsightReport second = adapter.fetchInsights(mapping, window);

        assertEquals(first.spendCents(), second.spendCents(), "同窗口数据拉取必须确定性可重现");
        assertEquals(first.impressions(), second.impressions());
        assertEquals(first.clicks(), second.clicks());
    }
}
