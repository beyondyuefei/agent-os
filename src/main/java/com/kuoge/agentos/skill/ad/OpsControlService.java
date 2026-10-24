package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.capability.CapabilityCode;
import com.kuoge.agentos.capability.CapabilityGateway;
import com.kuoge.agentos.capability.GatewayDecision;
import com.kuoge.agentos.channel.AdChannelAdapter;
import com.kuoge.agentos.channel.AdMapping;
import com.kuoge.agentos.delivery.DeliveryItem;
import com.kuoge.agentos.delivery.DeliveryItemAction;
import com.kuoge.agentos.delivery.DeliveryItemStore;
import com.kuoge.agentos.delivery.DeliveryItemStateMachine;
import com.kuoge.agentos.delivery.StateTransitionException;
import com.kuoge.agentos.skill.DomainService;
import com.kuoge.agentos.skill.SkillContext;

import java.util.List;
import java.util.UUID;

/**
 * ad.ops.control 启停控制（方案 3.4 / 8.5，确定性服务）。
 * 动作分类：ENABLE / RESUME = 风险动作（授权重检）；PAUSE / END = 停止类
 * （不受套餐门禁阻断，走独立审计路径，撤权后仍可执行且留痕）。
 * 表外迁移一律拒绝（状态机兜底）。
 * 启停动作真实调用平台通道，回执驱动：先调通道、成功才迁移状态（与创建执行语义一致）。
 */
public class OpsControlService implements DomainService<OpsControlService.Input, OpsControlService.Output> {

    public enum Action { ENABLE, PAUSE, RESUME, END }

    public record Input(String deliveryItemId, Action action) {}
    public record Output(String newStatus, String auditId) {}

    public static class AuthRecheckFailedException extends RuntimeException {
        public AuthRecheckFailedException(String code) { super("AUTH_RECHECK_FAILED: " + code); }
    }

    /** 平台通道不可用 / 调用失败（方案 8.5 错误码 CHANNEL_UNAVAILABLE） */
    public static class ChannelUnavailableException extends RuntimeException {
        public ChannelUnavailableException(String action, Throwable cause) {
            super("CHANNEL_UNAVAILABLE: " + action + " - " + cause.getMessage(), cause);
        }
    }

    private final CapabilityGateway gateway;
    private final DeliveryItemStore repository;
    private final DeliveryItemStateMachine stateMachine;
    private final AdChannelAdapter channelAdapter;
    private final PreflightCheckService preflightCheckService;

    public OpsControlService(CapabilityGateway gateway, DeliveryItemStore repository,
                             DeliveryItemStateMachine stateMachine, AdChannelAdapter channelAdapter,
                             PreflightCheckService preflightCheckService) {
        this.gateway = gateway;
        this.repository = repository;
        this.stateMachine = stateMachine;
        this.channelAdapter = channelAdapter;
        this.preflightCheckService = preflightCheckService;
    }

    @Override
    public Output execute(SkillContext ctx, Input input) {
        DeliveryItem item = repository.findById(input.deliveryItemId())
                .orElseThrow(() -> new IllegalArgumentException("ITEM_NOT_FOUND: " + input.deliveryItemId()));
        return switch (input.action()) {
            // 风险动作：授权重检
            case ENABLE -> riskAction(ctx, item, "AD_ENABLE", DeliveryItemAction.ENABLE, input.action());
            case RESUME -> riskAction(ctx, item, "AD_RESUME", DeliveryItemAction.RESUME, input.action());
            // 停止类动作：不受套餐阻断，独立审计路径
            case PAUSE -> stopAction(ctx, item, DeliveryItemAction.PAUSE, input.action());
            case END -> stopAction(ctx, item, DeliveryItemAction.END, input.action());
        };
    }

    private Output riskAction(SkillContext ctx, DeliveryItem item, String gatewayAction,
                              DeliveryItemAction transition, Action action) {
        GatewayDecision decision = gateway.check(item.getTenantId(), CapabilityCode.AD_DELIVERY_OPS,
                gatewayAction, item.getDeliveryItemId(), List.of());
        if (decision.rejected()) {
            repository.appendAudit(item.getTenantId() + " " + action + " recheck failed: "
                    + decision.rejectCode());
            throw new AuthRecheckFailedException(decision.rejectCode().name());
        }
        // 投前检查集 B（创建/启用/恢复前置）：B3 五项重检与网关判定重叠是方案本意——
        // 网关是门禁（放行/拒绝），preflight 出结构化报告（逐项 code），两者分工不合并
        PreflightCheckService.Report report = preflightCheckService.execute(ctx,
                new PreflightCheckService.Input(item.getDeliveryItemId(), PreflightCheckService.CheckSet.B));
        if (!report.pass()) {
            repository.appendAudit(item.getTenantId() + " " + action + " preflight B failed: "
                    + failedCodes(report));
            throw new PreflightBFailedException(failedCodes(report));
        }
        callChannel(item, action, transition);
        stateMachine.transit(item, transition, action.name());
        repository.save(item);
        String auditId = UUID.randomUUID().toString();
        repository.appendAudit(item.getTenantId() + " " + action + " -> " + item.getStatus()
                + " auditId=" + auditId);
        return new Output(item.getStatus().name(), auditId);
    }

    private Output stopAction(SkillContext ctx, DeliveryItem item,
                              DeliveryItemAction transition, Action action) {
        // 停止类仅豁免套餐到期 / 撤权阻断；状态机校验仍然生效
        callChannel(item, action, transition);
        stateMachine.transit(item, transition, action.name());
        repository.save(item);
        String auditId = UUID.randomUUID().toString();
        repository.appendAudit(item.getTenantId() + " " + action + " -> " + item.getStatus()
                + " auditId=" + auditId);
        return new Output(item.getStatus().name(), auditId);
    }

    /** 先调通道、成功才迁移状态；通道异常不迁移、不落库，留痕后抛 CHANNEL_UNAVAILABLE */
    private void callChannel(DeliveryItem item, Action action, DeliveryItemAction transition) {
        // 表外迁移在触达通道前即拒绝（状态机校验仍生效；真正迁移在通道成功之后）
        if (!stateMachine.canTransit(item.getStatus(), transition)) {
            throw new StateTransitionException("STATE_TRANSITION_INVALID: "
                    + item.getStatus() + " --" + transition + "--> (not in transition table)");
        }
        AdMapping mapping = mappingOf(item);
        try {
            switch (action) {
                case ENABLE, RESUME -> channelAdapter.enable(mapping);
                case PAUSE -> channelAdapter.pause(mapping);
                case END -> channelAdapter.end(mapping);
            }
        } catch (RuntimeException e) {
            repository.appendAudit(item.getTenantId() + " " + action + " channel call failed: "
                    + e.getMessage());
            throw new ChannelUnavailableException(action.name(), e);
        }
    }

    /** 由投放项构造平台映射；platformAdId 缺失（尚未创建成功）时明确拒绝 */
    private AdMapping mappingOf(DeliveryItem item) {
        if (item.getPlatformAdId() == null) {
            throw new IllegalStateException("PLATFORM_AD_ID_MISSING: " + item.getDeliveryItemId());
        }
        return new AdMapping("map_" + item.getDeliveryItemId(), item.getDeliveryItemId(),
                item.getTenantId(), item.getPlatformAdId(), null, null);
    }

    private List<String> failedCodes(PreflightCheckService.Report report) {
        return report.checks().stream()
                .filter(c -> !c.pass())
                .map(PreflightCheckService.Check::code)
                .toList();
    }
}
