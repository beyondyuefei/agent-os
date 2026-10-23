package com.kuoge.agentos.api;

import com.kuoge.agentos.skill.md.SkillLibrary;
import com.kuoge.agentos.skill.md.SkillOverrideStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Skill 模板运营后台逻辑测试：保存校验、生效内容来源（SEED / DB_OPS / CLASSPATH）、
 * 修改状态识别、回退。
 */
class SkillAdminServiceTest {

    private static class InMemoryStore implements SkillOverrideStore {
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

        /** 模拟启动种子导入 */
        void seed(String skillName, String reference, String content) {
            saveContent(skillName, reference, content, SEED_AUTHOR);
        }
    }

    private InMemoryStore store;
    private SkillAdminService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        service = new SkillAdminService(new SkillLibrary(store), store);
    }

    @Test
    void effectiveContentMarkedWithSource() {
        // 无记录 → classpath 兜底
        SkillAdminService.ContentView view = service.effectiveContent("ad-review-draft", null);
        assertEquals("CLASSPATH", view.source());

        // 种子导入 → SEED（默认值，未被运营改过）
        store.seed("ad-review-draft", null, """
                ---
                name: ad-review-draft
                description: 种子版
                ---
                种子 body
                """);
        view = service.effectiveContent("ad-review-draft", null);
        assertEquals("SEED", view.source());
        assertEquals("种子 body", view.content());

        // 运营保存 → DB_OPS
        service.saveOverride("ad-review-draft", null, """
                ---
                name: ad-review-draft
                description: 改版
                ---
                新内容
                """, "ops_user");
        view = service.effectiveContent("ad-review-draft", null);
        assertEquals("DB_OPS", view.source());
        assertEquals("新内容", view.content());
    }

    @Test
    void malformedBodyRejectedAtSaveTime() {
        assertThrows(IllegalArgumentException.class,
                () -> service.saveOverride("ad-review-draft", null, "没有 frontmatter 的内容", "ops"),
                "body 保存必须通过 SKILL.md 格式校验，防止运行时渲染失败");
    }

    @Test
    void listDistinguishesSeedFromOpsModified() {
        store.seed("ad-evolve-suggest", "RULE_CTR_LOW.md", "默认话术 {{ctrLow}}");
        store.seed("ad-evolve-suggest", "RULE_CLICK_NO_CONVERT.md", "默认话术");
        service.saveOverride("ad-evolve-suggest", "RULE_CTR_LOW.md", "运营话术 {{ctrLow}}", "ops");

        SkillAdminService.SkillView view = service.list().stream()
                .filter(v -> v.metadata().name().equals("ad-evolve-suggest"))
                .findFirst().orElseThrow();
        assertFalse(view.bodyModified());
        assertEquals(List.of("RULE_CTR_LOW.md"), view.modifiedReferences(),
                "种子行不算运营修改，只有 DB_OPS 行算");
    }

    @Test
    void detailExposesReferencesForEditor() {
        SkillAdminService.SkillDetailView detail = service.detail("ad-evolve-suggest");
        assertTrue(detail.references().contains("RULE_CTR_LOW.md"));
        assertEquals("all", detail.metadata().plans());
        assertThrows(IllegalArgumentException.class, () -> service.detail("no-such-skill"));
    }

    @Test
    void revertRestoresClasspathContent() {
        service.saveOverride("ad-evolve-suggest", "RULE_CTR_LOW.md", "临时话术", "ops");
        service.revert("ad-evolve-suggest", "RULE_CTR_LOW.md");

        SkillAdminService.ContentView view = service.effectiveContent("ad-evolve-suggest", "RULE_CTR_LOW.md");
        assertEquals("CLASSPATH", view.source());
        assertTrue(view.content().contains("建议更换素材"));
    }
}
