package com.kuoge.agentos.core.hook;

import java.util.Collections;
import java.util.List;

public record HookResult(
        boolean denied,
        boolean failed,
        boolean cancelled,
        List<String> messages,
        String updatedInput
) {

    public HookResult {
        messages = messages == null ? Collections.emptyList() : List.copyOf(messages);
    }

    public static HookResult allow() {
        return new HookResult(false, false, false, Collections.emptyList(), null);
    }

    public static HookResult deny(String reason) {
        return new HookResult(true, false, false, List.of(reason), null);
    }

    public static HookResult withUpdatedInput(String input) {
        return new HookResult(false, false, false, Collections.emptyList(), input);
    }
}
