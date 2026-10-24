package com.kuoge.agentos.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuoge.agentos.business.ads.InMemoryCollectionReadiness;
import com.kuoge.agentos.capability.CapabilityStore;
import com.kuoge.agentos.capability.CapabilityTestFixtures;
import com.kuoge.agentos.delivery.BudgetLedger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 投流工作台 REST 冒烟（SOP-A 主链路 + 观察快照）：
 * 建项 → 送审 → 批准 → 创建 → 启用 → observation 200；错误映射 400/409。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.ai.openai.api-key=dummy-key-for-test",
        "agentos.ads.ingest-tick-ms=3600000",
        "agentos.ads.ingest-initial-delay-ms=3600000"
})
class AdsControllerTest {

    private static final String TENANT = CapabilityTestFixtures.TENANT_KA;

    @MockBean
    private ChatModel chatModel;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private CapabilityStore capabilityStore;
    @Autowired
    private BudgetLedger budgetLedger;
    @Autowired
    private InMemoryCollectionReadiness collectionReadiness;

    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        CapabilityTestFixtures.populate(capabilityStore);
        try {
            budgetLedger.openAccount(TENANT, 1_000_000);
        } catch (RuntimeException ignored) {
            // 上下文复用时账户已存在
        }
    }

    @Test
    void sopA_smoke_createToEnableThenObservation() throws Exception {
        // 母集合就绪
        mvc.perform(post("/api/ads/collections/col1/ready").header("X-Tenant-Id", TENANT))
                .andExpect(status().isOk());

        // 建项
        MvcResult created = mvc.perform(post("/api/ads/items")
                        .header("X-Tenant-Id", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"imageId":"img1","productId":"p1","planId":"plan1",
                                 "collectionId":"col1","budgetAmount":10000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String itemId = om.readTree(created.getResponse().getContentAsString())
                .get("deliveryItemId").asText();

        // 送审 → 批准 → 创建 → 启用
        mvc.perform(post("/api/ads/items/{id}/submit", itemId).header("X-Tenant-Id", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_REVIEW"));
        mvc.perform(post("/api/ads/items/{id}/approve", itemId)
                        .header("X-Tenant-Id", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"boss\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(post("/api/ads/items/{id}/create", itemId).header("X-Tenant-Id", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        mvc.perform(post("/api/ads/items/{id}/ops", itemId)
                        .header("X-Tenant-Id", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ENABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newStatus").value("RUNNING"));

        // 观察快照 / 详情 / 列表 / 审计
        mvc.perform(get("/api/ads/items/{id}/observation", itemId).header("X-Tenant-Id", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryItemId").value(itemId));
        mvc.perform(get("/api/ads/items/{id}", itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
        mvc.perform(get("/api/ads/items").header("X-Tenant-Id", TENANT))
                .andExpect(status().isOk());
        mvc.perform(get("/api/ads/items/{id}/audit", itemId))
                .andExpect(status().isOk());
    }

    @Test
    void submitBlocked_whenCollectionNotReady() throws Exception {
        // 不就绪的集合：建项转待补，送审被 preflight A 阻断保持 PENDING（200 + 业务态）
        MvcResult created = mvc.perform(post("/api/ads/items")
                        .header("X-Tenant-Id", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"imageId":"img9","productId":"p9","planId":"plan1",
                                 "collectionId":"col_not_ready","budgetAmount":10000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String itemId = om.readTree(created.getResponse().getContentAsString())
                .get("deliveryItemId").asText();
        mvc.perform(post("/api/ads/items/{id}/submit", itemId).header("X-Tenant-Id", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.rejectReason").value(
                        org.hamcrest.Matchers.containsString("PREFLIGHT_A_FAILED")));
    }

    @Test
    void errorMapping_itemNotFoundAndInvalidTransition() throws Exception {
        // 不存在 → 400
        mvc.perform(get("/api/ads/items/di_404"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        // 表外迁移（PENDING 直接 approve）→ 409
        collectionReadiness.markReady("col2");
        MvcResult created = mvc.perform(post("/api/ads/items")
                        .header("X-Tenant-Id", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"imageId":"img2","productId":"p2","planId":"plan1",
                                 "collectionId":"col2","budgetAmount":10000}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = om.readTree(created.getResponse().getContentAsString());
        mvc.perform(post("/api/ads/items/{id}/approve", node.get("deliveryItemId").asText())
                        .header("X-Tenant-Id", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"boss\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("STATE_TRANSITION_INVALID")));
    }
}
