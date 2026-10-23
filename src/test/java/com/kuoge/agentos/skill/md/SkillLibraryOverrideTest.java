package com.kuoge.agentos.skill.md;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillLibrary 数据库覆盖优先级测试（解析顺序：DB 覆盖 > classpath 默认）。
 */
class SkillLibraryOverrideTest {

    /** 内存版覆盖存储 */
    private static class InMemoryOverrideStore implements SkillOverrideStore {
        private final Map<String, OverrideEntry> data = new HashMap<>();

        @Override
        public Optional<OverrideEntry> findEntry(String skillName, String reference) {
            return Optional.ofNullable(data.get(key(skillName, reference)));
        }

        @Override
        public void saveContent(String skillName, String reference, String content, String updatedBy) {
            data.put(key(skillName, reference), new OverrideEntry(skillName, reference, content, updatedBy));
        }

        @Override
        public void deleteContent(String skillName, String reference) {
            data.remove(key(skillName, reference));
        }

        @Override
        public List<String> listOverrides() {
            return new ArrayList<>(data.keySet());
        }

        private static String key(String skillName, String reference) {
            return reference == null || reference.isBlank() ? skillName : skillName + "/" + reference;
        }
    }

    private InMemoryOverrideStore store;
    private SkillLibrary library;

    @BeforeEach
    void setUp() {
        store = new InMemoryOverrideStore();
        library = new SkillLibrary(store);
    }

    @Test
    void classpathDefaultWhenNoOverride() {
        assertTrue(library.load("ad-review-draft").body().contains("投流复盘助手"),
                "无覆盖时应读 classpath 默认模板");
        assertTrue(library.loadReference("ad-review-draft", "four-section-format.md").contains("四段式"));
    }

    @Test
    void dbOverrideTakesPrecedenceOverClasspath() {
        store.saveContent("ad-evolve-suggest", "RULE_CTR_LOW.md",
                "运营修改后的 CTR 话术 {{ctrLow}}", "ops_user");

        String wording = PromptTemplate.render(
                library.loadReference("ad-evolve-suggest", "RULE_CTR_LOW.md"),
                Map.of("ctrLow", 0.02));
        assertTrue(wording.contains("运营修改后的 CTR 话术"), "DB 覆盖应优先于 classpath");
        assertFalse(wording.contains("{{"), "覆盖模板同样走渲染与 fail-fast");
    }

    @Test
    void bodyOverrideFromDb() {
        String custom = """
                ---
                name: ad-review-draft
                description: 运营改版复盘助手
                version: 1.x
                ---
                自定义 body，快照 spend={{spendCents}}
                """;
        store.saveContent("ad-review-draft", null, custom, "ops_user");

        SkillDocument doc = library.load("ad-review-draft");
        assertEquals("运营改版复盘助手", doc.metadata().description(), "元数据也可被覆盖");
        assertTrue(doc.body().contains("自定义 body"));
    }

    @Test
    void revertFallsBackToClasspath() {
        store.saveContent("ad-evolve-suggest", "RULE_CTR_LOW.md", "临时话术 {{ctrLow}}", "ops");
        assertTrue(library.loadReference("ad-evolve-suggest", "RULE_CTR_LOW.md").contains("临时话术"));

        store.deleteContent("ad-evolve-suggest", "RULE_CTR_LOW.md");
        assertTrue(library.loadReference("ad-evolve-suggest", "RULE_CTR_LOW.md").contains("建议更换素材"),
                "删除覆盖后应回退 classpath 默认");
    }

    @Test
    void invalidateRefreshesCachedBody() {
        library.load("ad-review-draft");   // 触发缓存
        store.saveContent("ad-review-draft", null, """
                ---
                name: ad-review-draft
                description: v2
                ---
                新 body
                """, "ops");
        library.invalidate("ad-review-draft");

        assertEquals("新 body", library.load("ad-review-draft").body(),
                "invalidate 后应读到覆盖内容");
    }
}
