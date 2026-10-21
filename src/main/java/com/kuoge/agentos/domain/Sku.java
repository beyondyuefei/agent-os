package com.kuoge.agentos.domain;

public record Sku(
        String skuId,
        String platformSkuId,
        double cost,
        double price,
        int stock,
        double weightKg,
        String logisticsMode
) {
}
