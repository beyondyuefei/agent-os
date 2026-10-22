package com.kuoge.agentos.skill.md;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SKILL.md 解析器与模板渲染测试。
 */
class SkillMdParserTest {

    private static final String SAMPLE = """
            ---
            name: demo-skill
            description: 演示技能
            version: 1.x
            form: ai-skill
            risk: READ
            plans: all
            ---

            你是助手。参数：{{value}}
            """;

    @Test
    void parseFrontmatterAndBody() {
        SkillMdParser.Parsed parsed = SkillMdParser.parse(SAMPLE, "demo");
        assertEquals("demo-skill", parsed.metadata().name());
        assertEquals("演示技能", parsed.metadata().description());
        assertEquals("1.x", parsed.metadata().version());
        assertEquals("ai-skill", parsed.metadata().form());
        assertEquals("READ", parsed.metadata().risk());
        assertTrue(parsed.body().contains("你是助手"));
        assertFalse(parsed.body().contains("---"), "body 不应残留 frontmatter 分隔符");
    }

    @Test
    void missingFrontmatterRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SkillMdParser.parse("没有 frontmatter", "bad"));
    }

    @Test
    void missingRequiredKeyRejected() {
        String raw = "---\nversion: 1.x\n---\nbody";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SkillMdParser.parse(raw, "bad"));
        assertTrue(ex.getMessage().contains("name"));
    }

    @Test
    void renderReplacesAllPlaceholders() {
        String out = PromptTemplate.render("a={{x}}, b={{ y }}", Map.of("x", 1, "y", "two"));
        assertEquals("a=1, b=two", out);
    }

    @Test
    void missingVariableFailsFast() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PromptTemplate.render("值={{missing}}", Map.of()));
        assertTrue(ex.getMessage().contains("missing"), "缺失变量必须 fail-fast，防止空洞提示词进入模型");
    }
}
