package com.kuoge.agentos.delivery;

/**
 * 表外迁移异常：一律拒绝（方案 8.5 STATE_TRANSITION_INVALID）。
 */
public class StateTransitionException extends RuntimeException {

    public StateTransitionException(String message) {
        super(message);
    }
}
