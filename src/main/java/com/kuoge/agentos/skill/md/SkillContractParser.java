package com.kuoge.agentos.skill.md;

import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * contract.yaml 解析器。格式：
 * <pre>
 * name: ad-evolve-suggest
 * description: ...
 * version: all
 * form: ai-skill
 * risk: READ
 * plans: all
 * templates:
 *   RULE_CTR_LOW:
 *     variables:
 *       ctrLow: { type: number, required: true, desc: CTR 阈值 }
 *     default: "CTR 低于阈值 {{ctrLow}}，建议更换素材或调整人群定向"
 * </pre>
 */
public final class SkillContractParser {

    private SkillContractParser() {}

    @SuppressWarnings("unchecked")
    public static SkillContract parse(String raw, String sourceName) {
        Map<String, Object> yaml = new Yaml().load(raw);
        if (yaml == null) {
            throw new IllegalArgumentException("Empty contract: " + sourceName);
        }
        SkillMetadata metadata = new SkillMetadata(
                required(yaml, "name", sourceName),
                required(yaml, "description", sourceName),
                str(yaml, "version"), str(yaml, "form"), str(yaml, "risk"), str(yaml, "plans"));

        Map<String, TemplateContract> templates = new LinkedHashMap<>();
        Object templatesNode = yaml.get("templates");
        if (templatesNode instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                templates.put(key, parseTemplate(entry.getValue(), sourceName + ":" + key));
            }
        }
        if (templates.isEmpty()) {
            throw new IllegalArgumentException("Contract declares no templates: " + sourceName);
        }
        return new SkillContract(metadata, templates);
    }

    @SuppressWarnings("unchecked")
    private static TemplateContract parseTemplate(Object node, String source) {
        if (!(node instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Malformed template node: " + source);
        }
        Map<String, VariableContract> variables = new LinkedHashMap<>();
        Object varsNode = ((Map<String, Object>) map).get("variables");
        if (varsNode instanceof Map<?, ?> vars) {
            for (Map.Entry<?, ?> var : vars.entrySet()) {
                String varName = String.valueOf(var.getKey());
                if (var.getValue() instanceof Map<?, ?> spec) {
                    Map<String, Object> specMap = (Map<String, Object>) spec;
                    variables.put(varName, new VariableContract(
                            str(specMap, "type"),
                            Boolean.parseBoolean(str(specMap, "required")),
                            str(specMap, "desc")));
                } else {
                    variables.put(varName, new VariableContract("string", true, ""));
                }
            }
        }
        Object defaultContent = ((Map<String, Object>) map).get("default");
        return new TemplateContract(variables,
                defaultContent == null ? "" : String.valueOf(defaultContent).strip());
    }

    private static String required(Map<String, Object> yaml, String key, String source) {
        String value = str(yaml, key);
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "Contract missing required key '" + key + "': " + source);
        }
        return value;
    }

    private static String str(Map<String, Object> yaml, String key) {
        Object value = yaml.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
