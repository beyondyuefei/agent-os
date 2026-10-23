package com.kuoge.agentos.skill.md;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * contract.yaml 解析与模板变量工具测试。
 */
class SkillContractParserTest {

    private static final String SAMPLE = """
            name: demo-skill
            description: 演示技能
            version: 1.x
            form: ai-skill
            risk: READ
            plans: all
            templates:
              BODY:
                variables:
                  name: { type: string, required: true, desc: 名称 }
                  count: { type: number, required: false }
                default: "你好 {{name}}，共 {{count}} 条"
              RULE_X:
                variables: {}
                default: "固定文案"
            """;

    @Test
    void parseMetadataAndTemplates() {
        SkillContract contract = SkillContractParser.parse(SAMPLE, "demo");
        assertEquals("demo-skill", contract.metadata().name());
        assertEquals("演示技能", contract.metadata().description());
        assertEquals("1.x", contract.metadata().version());
        assertEquals(2, contract.templates().size());

        TemplateContract body = contract.requireTemplate("BODY");
        assertTrue(body.variables().get("name").required());
        assertFalse(body.variables().get("count").required());
        assertEquals("number", body.variables().get("count").type());
        assertTrue(body.defaultContent().contains("{{name}}"));

        assertThrows(IllegalArgumentException.class, () -> contract.requireTemplate("NO_SUCH"),
                "未声明的模板键必须拒绝");
    }

    @Test
    void missingRequiredKeyRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SkillContractParser.parse("description: x\ntemplates:\n  A:\n    default: hi", "bad"));
    }

    @Test
    void noTemplatesRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SkillContractParser.parse("name: a\ndescription: b", "bad"),
                "契约必须至少声明一个模板");
    }

    @Test
    void renderAndExtract() {
        assertEquals("a=1, b=two", PromptTemplate.render("a={{x}}, b={{ y }}", Map.of("x", 1, "y", "two")));
        assertEquals(Set.of("x", "y"), PromptTemplate.extractVariables("{{x}} {{ y }} {{x}}"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PromptTemplate.render("值={{missing}}", Map.of()));
        assertTrue(ex.getMessage().contains("missing"), "缺失变量必须 fail-fast");
    }
}
