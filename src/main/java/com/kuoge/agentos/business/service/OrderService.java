package com.kuoge.agentos.business.service;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 订单服务 SPI —— 对接 OMS / 订单中心。
 */
public interface OrderService {

    JsonNode queryOrders(String tenantId, int pageNo, int pageSize);

    JsonNode getOrderDetail(String tenantId, String orderNo);
}
