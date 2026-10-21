package com.kuoge.agentos.delivery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 投放项状态机测试（方案第六章）：每个状态迁移都是一条可测试规则；表外迁移一律拒绝。
 */
class DeliveryItemStateMachineTest {

    private DeliveryItemStateMachine sm;
    private DeliveryItem item;

    @BeforeEach
    void setUp() {
        sm = new DeliveryItemStateMachine();
        item = new DeliveryItem("di_1", "t1", "img_1", "p_1", "plan_1", "col_1",
                DeliveryItemStatus.PENDING);
    }

    @Test
    void happyPath_pendingToRunning() {
        sm.transit(item, DeliveryItemAction.SUBMIT, null);
        assertEquals(DeliveryItemStatus.READY_FOR_REVIEW, item.getStatus());
        sm.transit(item, DeliveryItemAction.APPROVE, null);
        assertEquals(DeliveryItemStatus.APPROVED, item.getStatus());
        sm.transit(item, DeliveryItemAction.CREATE_START, null);
        assertEquals(DeliveryItemStatus.CREATING, item.getStatus());
        sm.transit(item, DeliveryItemAction.CREATE_SUCCESS, null);
        assertEquals(DeliveryItemStatus.CREATED, item.getStatus());
        sm.transit(item, DeliveryItemAction.ENABLE, null);
        assertEquals(DeliveryItemStatus.RUNNING, item.getStatus());
    }

    @Test
    void invalidTransition_rejected() {
        // 待补状态直接启用：表外迁移
        StateTransitionException ex = assertThrows(StateTransitionException.class,
                () -> sm.transit(item, DeliveryItemAction.ENABLE, null));
        assertTrue(ex.getMessage().contains("STATE_TRANSITION_INVALID"));
        assertEquals(DeliveryItemStatus.PENDING, item.getStatus(), "失败迁移不改变状态");
    }

    @Test
    void createdIsNotRunning_enableIsIndependentAction() {
        // "已创建 ≠ 运行中"：CREATED 状态 PAUSE 非法，ENABLE 才合法
        item.setStatus(DeliveryItemStatus.CREATED);
        assertThrows(StateTransitionException.class,
                () -> sm.transit(item, DeliveryItemAction.PAUSE, null));
        sm.transit(item, DeliveryItemAction.ENABLE, null);
        assertEquals(DeliveryItemStatus.RUNNING, item.getStatus());
    }

    @Test
    void rejectAndResubmit_bumpsVersionInvalidatesSnapshot() {
        sm.transit(item, DeliveryItemAction.SUBMIT, null);
        sm.transit(item, DeliveryItemAction.REJECT, "素材不达标");
        assertEquals(DeliveryItemStatus.RETURNED, item.getStatus());
        assertEquals("素材不达标", item.getRejectReason());
        item.setApprovedSnapshot(new ApprovedItemSnapshot(
                "snap1", item.getDeliveryItemId(), 1, 1, 1, "1.x",
                "p_1", "img_1", "link", java.util.List.of(), 100, "RESERVED", java.util.List.of()));
        int oldVersion = item.getVersion();
        sm.transit(item, DeliveryItemAction.RESUBMIT, null);
        assertEquals(oldVersion + 1, item.getVersion(), "修订产生新版本");
        assertNull(item.getApprovedSnapshot(), "旧批准快照随新版本失效");
        assertFalse(item.approvedAndLatest());
    }

    @Test
    void unknownThenRecheck_paths() {
        item.setStatus(DeliveryItemStatus.APPROVED);
        item.setApprovedSnapshot(new ApprovedItemSnapshot(
                "snap1", item.getDeliveryItemId(), item.getVersion(), 1, 1, "1.x",
                "p_1", "img_1", "link", java.util.List.of(), 100, "RESERVED", java.util.List.of()));
        sm.transit(item, DeliveryItemAction.CREATE_START, null);
        sm.transit(item, DeliveryItemAction.CREATE_UNKNOWN, null);
        assertEquals(DeliveryItemStatus.PENDING_CHECK, item.getStatus());
        // 反查确认已创建 → 已创建
        sm.transit(item, DeliveryItemAction.CONFIRM_CREATED, null);
        assertEquals(DeliveryItemStatus.CREATED, item.getStatus());
    }

    @Test
    void revokeEnd_fromRunningOrPaused_only() {
        item.setStatus(DeliveryItemStatus.RUNNING);
        sm.transit(item, DeliveryItemAction.REVOKE_END, null);
        assertEquals(DeliveryItemStatus.ENDED, item.getStatus());
        // 已结束不可再迁移
        assertThrows(StateTransitionException.class,
                () -> sm.transit(item, DeliveryItemAction.PAUSE, null));
    }

    @Test
    void pauseResumeCycle() {
        item.setStatus(DeliveryItemStatus.RUNNING);
        sm.transit(item, DeliveryItemAction.PAUSE, null);
        assertEquals(DeliveryItemStatus.PAUSED, item.getStatus());
        sm.transit(item, DeliveryItemAction.RESUME, null);
        assertEquals(DeliveryItemStatus.RUNNING, item.getStatus());
        sm.transit(item, DeliveryItemAction.END, null);
        assertEquals(DeliveryItemStatus.ENDED, item.getStatus());
    }
}
