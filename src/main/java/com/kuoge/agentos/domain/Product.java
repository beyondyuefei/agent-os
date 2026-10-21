package com.kuoge.agentos.domain;

import java.util.List;

public record Product(
        String productId,
        String name,
        String category,
        String description,
        List<String> images,
        List<Sku> skus,
        PlatformType platformType,
        String targetMarket,
        List<String> certifications
) {
}
