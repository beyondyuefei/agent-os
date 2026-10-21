package com.kuoge.agentos.core.tool;

import java.util.Map;

public interface Tool {

    String name();

    default String description() {
        return name();
    }

    String execute(Map<String, Object> arguments) throws Exception;
}
