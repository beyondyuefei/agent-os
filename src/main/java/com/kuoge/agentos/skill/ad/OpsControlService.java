package com.kuoge.agentos.skill.ad;

import com.kuoge.agentos.capability.CapabilityCode;
import com.kuoge.agentos.capability.CapabilityGateway;
import com.kuoge.agentos.capability.GatewayDecision;
import com.kuoge.agentos.delivery.DeliveryItem;
import com.kuoge.agentos.delivery.DeliveryItemAction;
import com.kuoge.agentos.delivery.DeliveryItemRepository;
import com.kuoge.agentos.delivery.DeliveryItemStateMachine;
import com.kuoge.agentos.skill.DomainService;
import com.kuoge.agentos.skill.SkillContext;

import java.util.List;
import java.util.UUID;

/**
 * ad.ops.control 启停控制（方案 3.4 / 8.5，确定性服务）。
 * 动作分类：ENABLE / RESUME = 风险动作（授权重检）；PAUSE / END = 停止类
 * （不受套餐门禁阻断，走独立审计路径，撤权后仍可执行且留痕）。
 * 表外迁移一律拒绝（状态机兜底）。
 */
public class OpsControlService implements DomainService<OpsControlService.Input, OpsControlService.Output> {

    public enum Action { ENABLE, PAUSE, RESUME, END }

    public record Input(String deliveryItemId, Action action) {}
    public record Output(String newStatus, String auditId) {}

    public static class AuthRecheckFailedException extends RuntimeException {
        public AuthRecheckFailedException(String code) { super("AUTH_RECHECK_FAILED: " + code); }
    }

    private final CapabilityGateway gateway;
    private final DeliveryItemRepository repository;
    private final DeliveryItemStateMachine stateMachine;

    public OpsControlService(CapabilityGateway gateway, DeliveryItemRepository repository,
                             DeliveryItemStateMachine stateMachine) {
        this.gateway = gateway;
        this.repository = repository;
        this.stateMachine = stateMachine;
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
        stateMachine.transit(item, transition, action.name());
        repository.save(item);
        String auditId = UUID.randomUUID().toString();
        repository.appendAudit(item.getTenantId() + " " + action + " -> " + item.getStatus()
                + " auditId=" + auditId);
        return new Output(item.getStatus().name(), auditId);
    }
}
