package com.kuoge.agentos.skill;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skill 版本化注册表（方案 3.1 / 3.2）：
 * 同 code 不同档位的版本线分开注册、分开验收。
 * 运行时由 plan_capability_binding 的 skill_version 把能力解析到唯一确定的 skill 版本。
 */
public class SkillRegistry {

    private final Map<String, SkillDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, Object> instances = new ConcurrentHashMap<>();

    private static String key(String code, String version) {
        return code + "@" + version;
    }

    public void register(SkillDefinition definition, Object skillInstance) {
        String key = key(definition.code(), definition.version());
        if (definitions.containsKey(key)) {
            throw new IllegalArgumentException("Skill already registered: " + key);
        }
        definitions.put(key, definition);
        instances.put(key, skillInstance);
    }

    public Optional<SkillDefinition> definition(String code, String version) {
        return Optional.ofNullable(definitions.get(key(code, version)));
    }

    /** 按绑定的版本线解析 Skill 实例（"套餐能力拆解"在 Skill 层的落点） */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> resolve(String code, String version) {
        return Optional.ofNullable((T) instances.get(key(code, version)));
    }
}
