package com.kuoge.agentos.api;

import com.kuoge.agentos.infrastructure.persistence.JpaSkillTemplateStore;
import com.kuoge.agentos.infrastructure.persistence.entity.SkillTemplateVersionEntity;
import com.kuoge.agentos.infrastructure.persistence.repository.SkillTemplateRepository;
import com.kuoge.agentos.infrastructure.persistence.repository.SkillTemplateVersionRepository;
import com.kuoge.agentos.skill.md.SkillLibrary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Skill 模板运营后台全流程测试（H2 内存库）：
 * 契约校验拦截、草稿 → 评审单号发布 → 快照历史 → 回滚。
 */
@DataJpaTest
class SkillAdminServiceTest {

    @Autowired
    private SkillTemplateRepository templateRepository;
    @Autowired
    private SkillTemplateVersionRepository versionRepository;

    private JpaSkillTemplateStore store;
    private SkillAdminService service;

    @BeforeEach
    void setUp() {
        store = new JpaSkillTemplateStore(templateRepository);
        service = new SkillAdminService(new SkillLibrary(store), store, versionRepository);
    }

    @Test
    void undeclaredVariableRejectedAtSaveTime() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.saveDraft("ad-evolve-suggest", "RULE_CTR_LOW",
                        "建议关注毛利率 {{margin}}", "ops"),
                "未声明变量必须在保存时被拦截");
        assertTrue(ex.getMessage().contains("margin"));
        assertTrue(ex.getMessage().contains("ctrLow"), "提示中应列出契约已声明的变量");
        assertTrue(versionRepository.findAll().isEmpty(), "被拦截的草稿不得落库");
    }

    @Test
    void draftPublishLifecycle() {
        // 保存草稿（不生效）
        service.saveDraft("ad-evolve-suggest", "RULE_CTR_LOW", "新话术 {{ctrLow}}", "ops");
        assertTrue(store.findEntry("ad-evolve-suggest", "RULE_CTR_LOW").isEmpty(),
                "草稿不得影响生效内容");
        assertEquals("DRAFT", service.draftContent("ad-evolve-suggest", "RULE_CTR_LOW").source());

        // 缺评审单号 → 拒绝发布
        assertThrows(IllegalArgumentException.class,
                () -> service.publish("ad-evolve-suggest", "RULE_CTR_LOW", "", "ops"));

        // 发布 → 生效 + 快照 PUBLISHED
        service.publish("ad-evolve-suggest", "RULE_CTR_LOW", "FEISHU-001", "ops");
        SkillAdminService.ContentView view = service.effectiveContent("ad-evolve-suggest", "RULE_CTR_LOW");
        assertEquals("新话术 {{ctrLow}}", view.content());
        assertEquals("DB_OPS", view.source());

        List<SkillAdminService.VersionView> history = service.history("ad-evolve-suggest", "RULE_CTR_LOW");
        assertEquals(1, history.size());
        assertEquals("PUBLISHED", history.get(0).status());
        assertEquals("FEISHU-001", history.get(0).reviewRef());
    }

    @Test
    void discardDraftRestoresEffectiveContent() {
        service.saveDraft("ad-evolve-suggest", "RULE_CTR_LOW", "草稿", "ops");
        service.discardDraft("ad-evolve-suggest", "RULE_CTR_LOW");
        assertEquals("CONTRACT_DEFAULT",
                service.draftContent("ad-evolve-suggest", "RULE_CTR_LOW").source(),
                "丢弃草稿后编辑器回到生效内容（契约默认兜底）");
    }

    @Test
    void rollbackPublishesNewVersionFromHistory() {
        service.saveDraft("ad-evolve-suggest", "RULE_CTR_LOW", "v2 话术", "ops");
        service.publish("ad-evolve-suggest", "RULE_CTR_LOW", "R-2", "ops");
        service.saveDraft("ad-evolve-suggest", "RULE_CTR_LOW", "v3 坏话术", "ops");
        service.publish("ad-evolve-suggest", "RULE_CTR_LOW", "R-3", "ops");
        assertEquals("v3 坏话术", service.effectiveContent("ad-evolve-suggest", "RULE_CTR_LOW").content());

        // 回滚到 v1（首个发布版本，内容为 "v2 话术"）
        service.rollback("ad-evolve-suggest", "RULE_CTR_LOW", 1, "R-4-rollback", "ops");
        assertEquals("v2 话术", service.effectiveContent("ad-evolve-suggest", "RULE_CTR_LOW").content());

        List<SkillAdminService.VersionView> history = service.history("ad-evolve-suggest", "RULE_CTR_LOW");
        assertEquals(3, history.size(), "回滚产生新版本，历史不被改写");
        assertTrue(history.get(0).reviewRef().contains("rollback of v1"));
    }

    @Test
    void listAndDetailExposeModifiedStatus() {
        service.saveDraft("ad-evolve-suggest", "RULE_CTR_LOW", "新话术 {{ctrLow}}", "ops");
        service.publish("ad-evolve-suggest", "RULE_CTR_LOW", "R-1", "ops");

        SkillAdminService.SkillView view = service.list().stream()
                .filter(v -> v.metadata().name().equals("ad-evolve-suggest"))
                .findFirst().orElseThrow();
        assertEquals(List.of("RULE_CTR_LOW"), view.modifiedTemplates());

        SkillAdminService.SkillDetailView detail = service.detail("ad-evolve-suggest");
        assertTrue(detail.templates().stream().anyMatch(t -> t.key().equals("RULE_CTR_LOW")
                && t.variables().containsKey("ctrLow") && "DB_OPS".equals(t.source())));
        assertThrows(IllegalArgumentException.class, () -> service.detail("no-such-skill"));
    }

    @Test
    void newDraftReplacesOldDraft() {
        service.saveDraft("ad-evolve-suggest", "RULE_CTR_LOW", "草稿一", "ops");
        service.saveDraft("ad-evolve-suggest", "RULE_CTR_LOW", "草稿二", "ops");
        long drafts = versionRepository.findAll().stream()
                .filter(v -> SkillTemplateVersionEntity.STATUS_DRAFT.equals(v.getStatus())).count();
        assertEquals(1, drafts, "同模板只保留一条草稿");
        assertEquals("草稿二", service.draftContent("ad-evolve-suggest", "RULE_CTR_LOW").content());
    }
}
