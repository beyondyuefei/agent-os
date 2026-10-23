package com.kuoge.agentos.skill.md;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillLibrary 契约驱动测试（基于 classpath 上真实的 skills/&ast;/contract.yaml）：
 * 契约常驻、内容按需（数据库发布优先 / 契约默认兜底）、渲染 fail-fast。
 */
class SkillLibraryTest {

    /** 内存版模板存储 */
    static class InMemoryStore implements SkillTemplateStore {
        final Map<String, TemplateEntry> data = new HashMap<>();

        @Override
        public Optional<TemplateEntry> findEntry(String skillName, String templateKey) {
            return Optional.ofNullable(data.get(skillName + "/" + templateKey));
        }

        @Override
        public void saveContent(String skillName, String templateKey, String content, String updatedBy) {
            data.put(skillName + "/" + templateKey,
                    new TemplateEntry(skillName, templateKey, content, updatedBy));
        }

        @Override
        public void deleteContent(String skillName, String templateKey) {
            data.remove(skillName + "/" + templateKey);
        }

        @Override
        public List<String> listEntries() {
            return new ArrayList<>(data.keySet());
        }
    }

    private Map<String, Object> reviewVars() {
        return Map.of("spendCents", 100, "impressions", 200, "clicks", 30,
                "attributedOrders", 4, "crossProductOrders", 1, "unknownOrders", 2,
                "evidenceRefs", "ad_fact_platform:ad_1");
    }

    @Test
    void catalogExposesContractMetadata() {
        SkillLibrary library = new SkillLibrary();
        assertEquals(3, library.catalog().size(), "应扫描到三个 contract.yaml");
        SkillMetadata meta = library.metadata("ad-evolve-suggest").orElseThrow();
        assertEquals("ai-skill", meta.form());
        assertEquals("all", meta.version());
    }

    @Test
    void contractDeclaresTemplatesAndVariables() {
        SkillLibrary library = new SkillLibrary();
        SkillContract contract = library.contract("ad-evolve-suggest");
        assertTrue(contract.templates().containsKey("RULE_CTR_LOW"));
        assertTrue(contract.requireTemplate("RULE_CTR_LOW").variables().get("ctrLow").required());
        assertThrows(IllegalArgumentException.class, () -> library.contract("no-such-skill"));
    }

    @Test
    void contentFallsBackToContractDefaultWithoutStore() {
        SkillLibrary library = new SkillLibrary();   // 无存储
        String content = library.content("ad-evolve-suggest", "RULE_CTR_LOW");
        assertTrue(content.contains("建议更换素材"), "无存储时读契约默认值");
    }

    @Test
    void publishedContentTakesPrecedenceOverDefault() {
        InMemoryStore store = new InMemoryStore();
        SkillLibrary library = new SkillLibrary(store);
        store.saveContent("ad-evolve-suggest", "RULE_CTR_LOW", "运营话术 {{ctrLow}}", "ops");

        String wording = library.render("ad-evolve-suggest", "RULE_CTR_LOW", Map.of("ctrLow", 0.02));
        assertTrue(wording.contains("运营话术"), "数据库发布内容应优先于契约默认");
        assertFalse(wording.contains("{{"), "渲染不得残留占位符");
    }

    @Test
    void renderFailsFastOnMissingVariable() {
        SkillLibrary library = new SkillLibrary();
        Map<String, Object> vars = new HashMap<>(reviewVars());
        vars.remove("clicks");
        assertThrows(IllegalArgumentException.class,
                () -> library.render("ad-review-draft", "BODY", vars),
                "缺变量必须 fail-fast，不得带空洞进模型");
    }

    @Test
    void renderBodyTemplateWithAllVariables() {
        SkillLibrary library = new SkillLibrary();
        String prompt = library.render("ad-review-draft", "BODY", reviewVars());
        assertTrue(prompt.contains("spend = 100"));
        assertTrue(prompt.contains("证据指针：ad_fact_platform:ad_1"));
        assertFalse(prompt.contains("{{"));
    }
}
