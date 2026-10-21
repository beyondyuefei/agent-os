package com.kuoge.agentos.delivery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 预算台账测试（验收规则 6）：预算总账恒等式任意时刻成立 —— 已花 + 占用 + 预留 + 余额 = 授权总额。
 */
class BudgetLedgerTest {

    @Test
    void invariantHoldsThroughFullLifecycle() {
        BudgetLedger ledger = new BudgetLedger();
        ledger.openAccount("t1", 100_000);
        assertTrue(ledger.assertInvariant("t1"));

        assertTrue(ledger.reserve("t1", 30_000));
        assertTrue(ledger.assertInvariant("t1"), "预留后恒等式成立");

        ledger.occupyFromReserved("t1", 30_000);
        assertTrue(ledger.assertInvariant("t1"), "预留转占用后恒等式成立");

        assertTrue(ledger.occupyUnallocated("t1", 20_000));
        assertTrue(ledger.assertInvariant("t1"), "直接占用后恒等式成立");

        ledger.settle("t1", 25_000);
        assertTrue(ledger.assertInvariant("t1"), "结算后恒等式成立");
        assertEquals(25_000, ledger.account("t1").spent());
        assertEquals(25_000, ledger.account("t1").occupied());

        // 对账差异挂账不抹平
        ledger.reconcile("t1", 26_000);
        assertEquals(1_000, ledger.account("t1").discrepancy());
        assertTrue(ledger.assertInvariant("t1"), "对账挂账后恒等式仍成立");
    }

    @Test
    void insufficientBalance_reserveFails() {
        BudgetLedger ledger = new BudgetLedger();
        ledger.openAccount("t1", 10_000);
        assertFalse(ledger.reserve("t1", 20_000));
        assertTrue(ledger.assertInvariant("t1"));
    }

    @Test
    void occupyMoreThanReserved_throws() {
        BudgetLedger ledger = new BudgetLedger();
        ledger.openAccount("t1", 10_000);
        ledger.reserve("t1", 5_000);
        assertThrows(IllegalStateException.class, () -> ledger.occupyFromReserved("t1", 6_000));
    }
}
