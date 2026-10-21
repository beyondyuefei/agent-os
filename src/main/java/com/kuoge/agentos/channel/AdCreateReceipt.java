package com.kuoge.agentos.channel;

import java.util.Map;

/**
 * 广告创建回执（方案 3.3 ② / 8.4）。
 */
public record AdCreateReceipt(
        /** 平台请求 ID（幂等反查依据） */
        String platformRequestId,
        /** 平台广告 ID；UNKNOWN / FAILED 时为空 */
        String platformAdId,
        Status status,
        /** 原始回执（平台响应透传，审计用） */
        Map<String, Object> receipt
) {

    public enum Status {
        SUCCESS,
        FAILED,
        UNKNOWN
    }

    public AdCreateReceipt {
        receipt = receipt == null ? Map.of() : Map.copyOf(receipt);
    }

    public static AdCreateReceipt success(String requestId, String adId, Map<String, Object> receipt) {
        return new AdCreateReceipt(requestId, adId, Status.SUCCESS, receipt);
    }

    public static AdCreateReceipt failed(String requestId, Map<String, Object> receipt) {
        return new AdCreateReceipt(requestId, null, Status.FAILED, receipt);
    }

    public static AdCreateReceipt unknown(String requestId, Map<String, Object> receipt) {
        return new AdCreateReceipt(requestId, null, Status.UNKNOWN, receipt);
    }
}
