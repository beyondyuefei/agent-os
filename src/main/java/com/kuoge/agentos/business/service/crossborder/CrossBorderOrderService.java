package com.kuoge.agentos.business.service.crossborder;

import com.fasterxml.jackson.databind.JsonNode;
import com.kuoge.agentos.business.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 对接 CrossBorder 订单中心的 {@link OrderService} 实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrossBorderOrderService implements OrderService {

    private final CrossBorderApiClient apiClient;

    @Override
    public JsonNode queryOrders(String tenantId, int pageNo, int pageSize) {
        return apiClient.post("/api/orderAdmin/pageQueryOrders",
                Map.of("pageNo", pageNo, "pageSize", pageSize));
    }

    @Override
    public JsonNode getOrderDetail(String tenantId, String orderNo) {
        return apiClient.post("/api/orderAdmin/detail/" + orderNo, Map.of());
    }
}
