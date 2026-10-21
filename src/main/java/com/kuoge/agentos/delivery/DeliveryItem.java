package com.kuoge.agentos.delivery;

/**
 * 单图投放项：一期最小业务与授权单位（方案 1.2）。
 */
public class DeliveryItem {

    private final String deliveryItemId;
    private final String tenantId;
    private final String imageId;
    private final String productId;
    private final String planId;
    private final String collectionId;

    private DeliveryItemStatus status;
    private int version;
    private String rejectReason;
    private ApprovedItemSnapshot approvedSnapshot;
    private String platformRequestId;
    private String platformAdId;

    public DeliveryItem(String deliveryItemId, String tenantId, String imageId,
                        String productId, String planId, String collectionId,
                        DeliveryItemStatus status) {
        this.deliveryItemId = deliveryItemId;
        this.tenantId = tenantId;
        this.imageId = imageId;
        this.productId = productId;
        this.planId = planId;
        this.collectionId = collectionId;
        this.status = status;
        this.version = 1;
    }

    public String getDeliveryItemId() { return deliveryItemId; }
    public String getTenantId() { return tenantId; }
    public String getImageId() { return imageId; }
    public String getProductId() { return productId; }
    public String getPlanId() { return planId; }
    public String getCollectionId() { return collectionId; }
    public DeliveryItemStatus getStatus() { return status; }
    public int getVersion() { return version; }
    public String getRejectReason() { return rejectReason; }
    public ApprovedItemSnapshot getApprovedSnapshot() { return approvedSnapshot; }
    public String getPlatformRequestId() { return platformRequestId; }
    public String getPlatformAdId() { return platformAdId; }

    void setStatus(DeliveryItemStatus status) { this.status = status; }
    void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public void setApprovedSnapshot(ApprovedItemSnapshot snapshot) { this.approvedSnapshot = snapshot; }
    public void setPlatformRequestId(String platformRequestId) { this.platformRequestId = platformRequestId; }
    public void setPlatformAdId(String platformAdId) { this.platformAdId = platformAdId; }

    /** 修订产生新版本，旧批准快照随之失效 */
    void bumpVersion() {
        this.version++;
        this.approvedSnapshot = null;
    }

    /**
     * 批准快照有效且为当前最新版本（方案 2.2 判定矩阵 APR_005 检查项）。
     * 快照是投放项整个生命周期（创建 / 投放 / 暂停 / 结束）的批准依据，
     * 仅因修订升版本而失效，不随状态流转失效。
     */
    public boolean approvedAndLatest() {
        return approvedSnapshot != null
                && approvedSnapshot.itemVersion() == version;
    }
}
