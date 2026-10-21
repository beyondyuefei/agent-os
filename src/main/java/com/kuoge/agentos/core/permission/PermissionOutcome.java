package com.kuoge.agentos.core.permission;

public sealed interface PermissionOutcome {

    record Allow() implements PermissionOutcome {}

    record Deny(String reason) implements PermissionOutcome {}

    record Ask(String reason) implements PermissionOutcome {}
}
