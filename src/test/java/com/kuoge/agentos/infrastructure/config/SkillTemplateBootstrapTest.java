package com.kuoge.agentos.infrastructure.config;

import com.kuoge.agentos.infrastructure.persistence.JpaSkillTemplateStore;
import com.kuoge.agentos.infrastructure.persistence.entity.SkillTemplateVersionEntity;
import com.kuoge.agentos.infrastructure.persistence.repository.SkillTemplateRepository;
import com.kuoge.agentos.infrastructure.persistence.repository.SkillTemplateVersionRepository;
import com.kuoge.agentos.skill.md.SkillTemplateStore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 启动种子导入测试（H2 内存库）：
 * contract.yaml 默认内容作为数据库初始化数据；同时落 v1 版本快照；幂等；不冲掉运营发布。
 */
@DataJpaTest
class SkillTemplateBootstrapTest {

    @Autowired
    private SkillTemplateRepository templateRepository;
    @Autowired
    private SkillTemplateVersionRepository versionRepository;

    private SkillTemplateBootstrap bootstrap() {
        return new SkillTemplateBootstrap(templateRepository, versionRepository);
    }

    @Test
    void seedsAllContractsOnFirstRun() {
        bootstrap().run(null);

        JpaSkillTemplateStore store = new JpaSkillTemplateStore(templateRepository);
        for (String name : new String[]{"ad-review-draft", "ad-evolve-suggest", "ad-experience-distill"}) {
            assertFalse(templateRepository.findBySkillName(name).isEmpty(), name + " 应有种子记录");
        }
        SkillTemplateStore.TemplateEntry entry = store
                .findEntry("ad-evolve-suggest", "RULE_CTR_LOW").orElseThrow();
        assertEquals(SkillTemplateStore.SEED_AUTHOR, entry.updatedBy());
        assertTrue(entry.content().contains("{{ctrLow}}"), "种子内容为纯模板文本");

        // 版本快照同步落 v1
        SkillTemplateVersionEntity v1 = versionRepository
                .findFirstBySkillNameAndTemplateKeyAndStatusOrderByVersionNoDesc(
                        "ad-evolve-suggest", "RULE_CTR_LOW", SkillTemplateVersionEntity.STATUS_PUBLISHED)
                .orElseThrow();
        assertEquals(1, v1.getVersionNo());
        assertEquals("seed", v1.getReviewRef());
    }

    @Test
    void secondRunIsIdempotent() {
        bootstrap().run(null);
        long templates = templateRepository.count();
        long versions = versionRepository.count();
        bootstrap().run(null);
        assertEquals(templates, templateRepository.count(), "重复启动不得重复入库");
        assertEquals(versions, versionRepository.count());
    }

    @Test
    void opsPublishedContentNeverOverwrittenBySeed() {
        bootstrap().run(null);
        new JpaSkillTemplateStore(templateRepository)
                .saveContent("ad-evolve-suggest", "RULE_CTR_LOW", "运营话术 v9", "ops_user");
        bootstrap().run(null);

        assertEquals("运营话术 v9",
                new JpaSkillTemplateStore(templateRepository)
                        .findEntry("ad-evolve-suggest", "RULE_CTR_LOW").orElseThrow().content(),
                "种子导入绝不冲掉运营发布");
    }
}
