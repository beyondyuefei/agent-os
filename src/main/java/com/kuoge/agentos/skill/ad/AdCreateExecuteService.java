package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.capability.CapabilityCode;
import com.kuoge.agentos.capability.CapabilityGateway;
import com.kuoge.agentos.capability.GatewayDecision;
import com.kuoge.agentos.channel.AdChannelAdapter;
import com.kuoge.agentos.channel.AdCreateReceipt;
import com.kuoge.agentos.delivery.DeliveryItem;
import com.kuoge.agentos.delivery.DeliveryItemAction;
import com.kuoge.agentos.delivery.DeliveryItemStore;
import com.kuoge.agentos.delivery.DeliveryItemStateMachine;
import com.kuoge.agentos.skill.DomainService;
import com.kuoge.agentos.skill.SkillContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ad.create.execute 广告创建执行（方案 3.3 ② / 8.4，确定性服务，风险等级 write-high-risk）。
 * 工作流：网关门禁 → 幂等键 = hash(deliveryItemId + 批准版本号) → 查重（已成功不重建）
 * → 调 AdChannelAdapter.createAd → 回执落库 → UNKNOWN 进入待查反查。
 * 铁律：未知结果不自动重复创建，未确认前不得再次提交；模型不得绕过本服务直接触达渠道。
 */
public class AdCreateExecuteService implements DomainService<String, AdCreateExecuteService.Output> {

    /** 执行输出（方案 8.4 AdCreateOutput） */
    public record Output(String platformRequestId, String platformAdId,
                         AdCreateReceipt.Status status, boolean idempotentReplay) {}

    public static class GatewayRejectedException extends RuntimeException {
        public GatewayRejectedException(String code) { super("GATEWAY_REJECTED: " + code); }
    }

    private final CapabilityGateway gateway;
    private final AdChannelAdapter channelAdapter;
    private final DeliveryItemStore repository;
    private final DeliveryItemStateMachine stateMachine;
    private final PreflightCheckService preflightCheckService;
    /** 幂等键 → 已成功回执 */
    private final Map<String, Output> successReceipts = new ConcurrentHashMap<>();
    /** 幂等键 → 处理中（并发去重） */
    private final Map<String, Boolean> inFlight = new ConcurrentHashMap<>();

    public AdCreateExecuteService(CapabilityGateway gateway, AdChannelAdapter channelAdapter,
                                  DeliveryItemStore repository,
                                  DeliveryItemStateMachine stateMachine,
                                  PreflightCheckService preflightCheckService) {
        this.gateway = gateway;
        this.channelAdapter = channelAdapter;
        this.repository = repository;
        this.stateMachine = stateMachine;
        this.preflightCheckService = preflightCheckService;
    }

    @Override
    public Output execute(SkillContext ctx, String deliveryItemId) {
        DeliveryItem item = repository.findById(deliveryItemId)
                .orElseThrow(() -> new IllegalArgumentException("ITEM_NOT_FOUND: " + deliveryItemId));
        // 1. 网关门禁（风险动作 AD_CREATE：五项重检在网关内完成）
        GatewayDecision decision = gateway.check(ctx.tenantId(), CapabilityCode.AD_DELIVERY_OPS,
                "AD_CREATE", deliveryItemId, java.util.List.of());
        if (decision.rejected()) {
            throw new GatewayRejectedException(decision.rejectCode().name());
        }
        // 1.5 投前检查集 B（创建前置）：B3 五项重检与网关判定重叠是方案本意——
        // 网关是门禁（放行/拒绝），preflight 出结构化报告（逐项 code），两者分工不合并；
        // 不过则阻断且不触达渠道
        PreflightCheckService.Report report = preflightCheckService.execute(ctx,
                new PreflightCheckService.Input(deliveryItemId, PreflightCheckService.CheckSet.B));
        if (!report.pass()) {
            throw new PreflightBFailedException(report.checks().stream()
                    .filter(c -> !c.pass())
                    .map(PreflightCheckService.Check::code)
                    .toList());
        }
        String idempotencyKey = idempotencyKey(item);
        // 2. 查重：同键同内容命中已成功回执 → 返回既有结果（idempotent_replay，非错误）
        Output existing = successReceipts.get(idempotencyKey);
        if (existing != null) {
            return new Output(existing.platformRequestId(), existing.platformAdId(),
                    existing.status(), true);
        }
        // 2.5 待查守卫：创建状态未知未确认前，不得再次提交平台（验收规则 5）
        if (item.getStatus() == com.kuoge.agentos.delivery.DeliveryItemStatus.PENDING_CHECK) {
            return new Output(item.getPlatformRequestId(), null,
                    AdCreateReceipt.Status.UNKNOWN, false);
        }
        // 3. 并发去重：同键处理中 → 不重复提交平台
        if (inFlight.putIfAbsent(idempotencyKey, Boolean.TRUE) != null) {
            return new Output(null, null, AdCreateReceipt.Status.UNKNOWN, false);
        }
        try {
            // 3.5 获取执行权后二次校验：各防线之间存在时间窗，
            // 并发场景下其他线程可能已在本线程检查之后完成创建 / 转待查
            Output completed = successReceipts.get(idempotencyKey);
            if (completed != null) {
                return new Output(completed.platformRequestId(), completed.platformAdId(),
                        completed.status(), true);
            }
            if (item.getStatus() == com.kuoge.agentos.delivery.DeliveryItemStatus.PENDING_CHECK) {
                return new Output(item.getPlatformRequestId(), null,
                        AdCreateReceipt.Status.UNKNOWN, false);
            }
            // 限次重试：创建失败项走 RETRY_CREATE 回创建中；新项走 CREATE_START
            if (item.getStatus() == com.kuoge.agentos.delivery.DeliveryItemStatus.CREATE_FAILED) {
                stateMachine.transit(item, DeliveryItemAction.RETRY_CREATE, "limited retry");
            } else {
                stateMachine.transit(item, DeliveryItemAction.CREATE_START, "auto create");
            }
            repository.save(item);
            AdCreateReceipt receipt = channelAdapter.createAd(item.getApprovedSnapshot());
            item.setPlatformRequestId(receipt.platformRequestId());
            switch (receipt.status()) {
                case SUCCESS -> {
                    item.setPlatformAdId(receipt.platformAdId());
                    stateMachine.transit(item, DeliveryItemAction.CREATE_SUCCESS, "platform receipt");
                    Output output = new Output(receipt.platformRequestId(), receipt.platformAdId(),
                            AdCreateReceipt.Status.SUCCESS, false);
                    successReceipts.put(idempotencyKey, output);
                    return output;
                }
                case FAILED -> {
                    stateMachine.transit(item, DeliveryItemAction.CREATE_FAIL, "platform error");
                    return new Output(receipt.platformRequestId(), null,
                            AdCreateReceipt.Status.FAILED, false);
                }
                default -> {
                    // UNKNOWN → 待查，进入反查；未确认前不得再次提交
                    stateMachine.transit(item, DeliveryItemAction.CREATE_UNKNOWN, "timeout/no receipt");
                    return new Output(receipt.platformRequestId(), null,
                            AdCreateReceipt.Status.UNKNOWN, false);
                }
            }
        } finally {
            repository.save(item);
            inFlight.remove(idempotencyKey);
        }
    }

    /** 幂等键 = hash(deliveryItemId + 批准版本号) */
    String idempotencyKey(DeliveryItem item) {
        String raw = item.getDeliveryItemId() + "#" + item.getVersion();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
