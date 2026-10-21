package com.kuoge.agentos.orchestration.checkpoint;

/**
 * 检查点校验结果。
 */
public record ValidationResult(
        boolean passed,
        String message,
        Action action
) {

    public enum Action {
        PASS,
        RETRY,
        SKIP,
        STOP
    }

    public static ValidationResult pass(String message) {
        return new ValidationResult(true, message, Action.PASS);
    }

    public static ValidationResult retry(String message) {
        return new ValidationResult(false, message, Action.RETRY);
    }

    public static ValidationResult skip(String message) {
        return new ValidationResult(false, message, Action.SKIP);
    }

    public static ValidationResult stop(String message) {
        return new ValidationResult(false, message, Action.STOP);
    }
}
