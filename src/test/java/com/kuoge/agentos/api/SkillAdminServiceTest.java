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
 * Skill 模板运营后台逻辑测试：保存校验、生效内容来源、覆盖清单、回退。
 */
class SkillAdminServiceTest {

    private static class InMemoryStore implements SkillOverrideStore {
        private final Map<String, String> data = new HashMap<>();

        @Override
        public Optional<String> findContent(String skillName, String reference) {
            return Optional.ofNullable(data.get(
                    reference == null || reference.isBlank() ? skillName : skillName + "/" + reference));
        }

        @Override
        public void saveContent(String skillName, String reference, String content, String updatedBy) {
            data.put(reference == null || reference.isBlank() ? skillName : skillName + "/" + reference,
                    content);
        }

        @Override
        public void deleteContent(String skillName, String reference) {
            data.remove(reference == null || reference.isBlank() ? skillName : skillName + "/" + reference);
        }

        @Override
        public List<String> listOverrides() {
            return new ArrayList<>(data.keySet());
        }
    }

    private SkillAdminService service;

    @BeforeEach
    void setUp() {
        InMemoryStore store = new InMemoryStore();
        service = new SkillAdminService(new SkillLibrary(store), store);
    }

    @Test
    void effectiveContentMarkedWithSource() {
        SkillAdminService.ContentView view = service.effectiveContent("ad-review-draft", null);
        assertEquals("CLASSPATH", view.source());

        service.saveOverride("ad-review-draft", null, """
                ---
                name: ad-review-draft
                description: 改版
                ---
                新内容
                """, "ops_user");
        view = service.effectiveContent("ad-review-draft", null);
        assertEquals("DB", view.source());
        assertEquals("新内容", view.content());
    }

    @Test
    void malformedBodyRejectedAtSaveTime() {
        assertThrows(IllegalArgumentException.class,
                () -> service.saveOverride("ad-review-draft", null, "没有 frontmatter 的内容", "ops"),
                "body 覆盖必须通过 SKILL.md 格式校验，防止运行时渲染失败");
    }

    @Test
    void listShowsOverrideStatus() {
        service.saveOverride("ad-evolve-suggest", "RULE_CTR_LOW.md", "临时话术", "ops");

        SkillAdminService.SkillView view = service.list().stream()
                .filter(v -> v.metadata().name().equals("ad-evolve-suggest"))
                .findFirst().orElseThrow();
        assertFalse(view.bodyOverridden());
        assertEquals(List.of("RULE_CTR_LOW.md"), view.overriddenReferences());
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
