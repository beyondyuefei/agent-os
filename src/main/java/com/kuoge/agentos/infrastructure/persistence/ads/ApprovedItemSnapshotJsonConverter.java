package com.kuoge.agentos.infrastructure.persistence.ads;

import com.kuoge.agentos.delivery.ApprovedItemSnapshot;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 批准快照（方案 2.4 approved_item_snapshot）JSON 列转换器：
 * record 整体序列化为 JSON 字符串，避免过度建模（一期口径）。
 */
@Converter
public class ApprovedItemSnapshotJsonConverter
        implements AttributeConverter<ApprovedItemSnapshot, String> {

    @Override
    public String convertToDatabaseColumn(ApprovedItemSnapshot snapshot) {
        return snapshot == null ? null : AdsJsonSupport.toJson(snapshot);
    }

    @Override
    public ApprovedItemSnapshot convertToEntityAttribute(String json) {
        return json == null ? null : AdsJsonSupport.fromJson(json, ApprovedItemSnapshot.class);
    }
}
