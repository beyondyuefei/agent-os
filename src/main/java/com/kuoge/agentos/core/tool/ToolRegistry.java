package com.kuoge.agentos.core.tool;

import java.util.*;

public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public void register(Tool tool) {
        if (tools.containsKey(tool.name())) {
            throw new IllegalArgumentException("Tool already registered: " + tool.name());
        }
        tools.put(tool.name(), tool);
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public Collection<Tool> all() {
        return Collections.unmodifiableCollection(tools.values());
    }

    public ToolRegistry scoped(Set<String> allowed) {
        ToolRegistry scoped = new ToolRegistry();
        for (Tool tool : tools.values()) {
            if (allowed.contains(tool.name())) {
                scoped.register(tool);
            }
        }
        return scoped;
    }

    public String toPromptText() {
        StringBuilder sb = new StringBuilder();
        sb.append("You have access to the following tools:\n\n");
        for (Tool tool : tools.values()) {
            sb.append("- ").append(tool.name()).append(": ").append(tool.description()).append("\n");
        }
        sb.append("\nWhen you need to use a tool, respond with a ToolUse block.\n");
        return sb.toString();
    }
}
