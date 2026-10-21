package com.kuoge.agentos.delivery;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.kuoge.agentos.delivery.DeliveryItemStatus.*;

/**
 * 投放项状态机（方案第六章，P0）：状态迁移表的唯一实现。
 * 每个迁移都是一条可测试规则：前置状态 → 动作 → 目标状态；表外迁移一律拒绝。
 */
public class DeliveryItemStateMachine {

    private record Transition(DeliveryItemStatus target) {}

    private final Map<DeliveryItemAction, Map<DeliveryItemStatus, Transition>> table = new EnumMap<>(DeliveryItemAction.class);

    public DeliveryItemStateMachine() {
        register(DeliveryItemAction.SUBMIT, PENDING, READY_FOR_REVIEW);
        register(DeliveryItemAction.APPROVE, READY_FOR_REVIEW, APPROVED);
        register(DeliveryItemAction.REJECT, READY_FOR_REVIEW, RETURNED);
        register(DeliveryItemAction.RESUBMIT, RETURNED, READY_FOR_REVIEW);
        register(DeliveryItemAction.CREATE_START, APPROVED, CREATING);
        register(DeliveryItemAction.CREATE_SUCCESS, CREATING, CREATED);
        register(DeliveryItemAction.CREATE_FAIL, CREATING, CREATE_FAILED);
        register(DeliveryItemAction.CREATE_UNKNOWN, CREATING, PENDING_CHECK);
        register(DeliveryItemAction.CONFIRM_CREATED, PENDING_CHECK, CREATED);
        register(DeliveryItemAction.CONFIRM_NOT_CREATED, PENDING_CHECK, CREATE_FAILED);
        register(DeliveryItemAction.RETRY_CREATE, CREATE_FAILED, CREATING);
        register(DeliveryItemAction.ENABLE, CREATED, RUNNING);
        register(DeliveryItemAction.PAUSE, RUNNING, PAUSED);
        register(DeliveryItemAction.RESUME, PAUSED, RUNNING);
        registerMulti(DeliveryItemAction.END, EnumSet.of(RUNNING, PAUSED), ENDED);
        registerMulti(DeliveryItemAction.REVOKE_END, EnumSet.of(RUNNING, PAUSED), ENDED);
        registerMulti(DeliveryItemAction.CANCEL, EnumSet.of(APPROVED, READY_FOR_REVIEW), CANCELLED);
    }

    private void register(DeliveryItemAction action, DeliveryItemStatus from, DeliveryItemStatus to) {
        table.computeIfAbsent(action, a -> new EnumMap<>(DeliveryItemStatus.class))
                .put(from, new Transition(to));
    }

    private void registerMulti(DeliveryItemAction action, Set<DeliveryItemStatus> froms, DeliveryItemStatus to) {
        for (DeliveryItemStatus from : froms) {
            register(action, from, to);
        }
    }

    /** 是否允许迁移 */
    public boolean canTransit(DeliveryItemStatus from, DeliveryItemAction action) {
        Map<DeliveryItemStatus, Transition> byAction = table.get(action);
        return byAction != null && byAction.containsKey(from);
    }

    /**
     * 执行状态迁移（表外迁移一律拒绝，对应 8.5 STATE_TRANSITION_INVALID）。
     */
    public DeliveryItem transit(DeliveryItem item, DeliveryItemAction action, String note) {
        if (!canTransit(item.getStatus(), action)) {
            throw new StateTransitionException("STATE_TRANSITION_INVALID: "
                    + item.getStatus() + " --" + action + "--> (not in transition table)");
        }
        DeliveryItemStatus target = table.get(action).get(item.getStatus()).target();
        switch (action) {
            case REJECT -> item.setRejectReason(note);
            case RESUBMIT -> item.bumpVersion();   // 修订产生新版本，旧批准快照失效
            default -> { }
        }
        item.setStatus(target);
        return item;
    }
}
