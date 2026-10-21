package com.kuoge.agentos.domain;

public record ProfitReport(
        String productId,
        double sellingPrice,
        double productCost,
        double logisticsCost,
        double platformCommission,
        double estimatedTax,
        double grossProfit,
        double grossMarginRate,
        boolean profitable,
        String suggestion
) {
}
