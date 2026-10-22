package com.kuoge.agentos.skill.md;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillLibrary 三级渐进加载测试（基于 classpath 上真实的 skills/ad-review-draft）。
 */
class SkillLibraryTest {

    private final SkillLibrary library = new SkillLibrary();

    @Test
    void catalogExposesMetadataWithoutLoadingBody() {
        List<SkillMetadata> catalog = library.catalog();
        assertFalse(catalog.isEmpty(), "构造时应扫描到 skills/ 下的元数据");
        SkillMetadata meta = library.metadata("ad-review-draft").orElseThrow();
        assertEquals("ai-skill", meta.form());
        assertEquals("1.x", meta.version());
        assertFalse(meta.description().isEmpty());
    }

    @Test
    void bodyLoadedOnDemandAndCached() {
        SkillDocument doc = library.load("ad-review-draft");
        assertTrue(doc.body().contains("{{spendCents}}"), "body 含模板占位符");
        assertSame(doc, library.load("ad-review-draft"), "二次加载应命中缓存");
        assertTrue(doc.references().contains("four-section-format.md"),
                "references 清单应可见（第三级按需）");
    }

    @Test
    void referenceLoadedSeparately() {
        String ref = library.loadReference("ad-review-draft", "four-section-format.md");
        assertTrue(ref.contains("四段式"));
        assertThrows(IllegalArgumentException.class,
                () -> library.loadReference("ad-review-draft", "../escape.md"),
                "引用路径穿越必须被拒绝");
    }

    @Test
    void renderWithVariables() {
        String prompt = library.render("ad-review-draft", Map.of(
                "spendCents", 100, "impressions", 200, "clicks", 30,
                "attributedOrders", 4, "crossProductOrders", 1, "unknownOrders", 2,
                "evidenceRefs", "ad_fact_platform:ad_1"));
        assertTrue(prompt.contains("spend = 100"));
        assertTrue(prompt.contains("证据指针：ad_fact_platform:ad_1"));
    }

    @Test
    void unknownSkillRejected() {
        assertThrows(IllegalArgumentException.class, () -> library.load("no-such-skill"));
    }
}
