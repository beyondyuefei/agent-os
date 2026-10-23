package com.kuoge.agentos.infrastructure.config;

import com.kuoge.agentos.infrastructure.persistence.JpaSkillOverrideStore;
import com.kuoge.agentos.infrastructure.persistence.repository.SkillTemplateRepository;
import com.kuoge.agentos.skill.md.SkillOverrideStore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 启动种子导入测试（H2 内存库）：
 * jar 内 md 模板作为数据库初始化数据；幂等；不冲掉运营覆盖。
 */
@DataJpaTest
class SkillTemplateBootstrapTest {

    @Autowired
    private SkillTemplateRepository repository;

    @Test
    void seedsAllClasspathTemplatesOnFirstRun() {
        new SkillTemplateBootstrap(repository).run(null);

        JpaSkillOverrideStore store = new JpaSkillOverrideStore(repository);
        // 三个 skill 的 body 均入库，来源标记为种子
        for (String name : new String[]{"ad-review-draft", "ad-evolve-suggest", "ad-experience-distill"}) {
            SkillOverrideStore.OverrideEntry entry = store.findEntry(name, null).orElseThrow();
            assertEquals(SkillOverrideStore.SEED_AUTHOR, entry.updatedBy());
            assertTrue(entry.content().startsWith("---"), "body 存完整 SKILL.md 原文");
        }
        // references 同样入库
        assertTrue(store.findContent("ad-evolve-suggest", "RULE_CTR_LOW.md").orElseThrow()
                .contains("{{ctrLow}}"));
    }

    @Test
    void secondRunIsIdempotent() {
        new SkillTemplateBootstrap(repository).run(null);
        long count = repository.count();
        new SkillTemplateBootstrap(repository).run(null);
        assertEquals(count, repository.count(), "重复启动不得重复入库");
    }

    @Test
    void opsOverrideNeverOverwrittenBySeed() {
        new SkillTemplateBootstrap(repository).run(null);
        // 运营修改
        new JpaSkillOverrideStore(repository)
                .saveContent("ad-evolve-suggest", "RULE_CTR_LOW.md", "运营话术 v9", "ops_user");
        // 再次启动
        new SkillTemplateBootstrap(repository).run(null);

        assertEquals("运营话术 v9",
                new JpaSkillOverrideStore(repository)
                        .findContent("ad-evolve-suggest", "RULE_CTR_LOW.md").orElseThrow(),
                "种子导入绝不冲掉运营修改");
    }
}
