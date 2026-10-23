package com.kuoge.agentos.infrastructure.persistence;

import com.kuoge.agentos.infrastructure.persistence.repository.SkillTemplateRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JPA 模板存储测试（H2 内存库）：落库 / 更新 / 删除 / 清单 / 种子识别。
 */
@DataJpaTest
class JpaSkillTemplateStoreTest {

    @Autowired
    private SkillTemplateRepository repository;

    @Test
    void saveUpdateDeleteRoundTrip() {
        JpaSkillTemplateStore store = new JpaSkillTemplateStore(repository);

        store.saveContent("ad-evolve-suggest", "RULE_CTR_LOW", "话术 v1", "ops");
        assertEquals("话术 v1", store.findEntry("ad-evolve-suggest", "RULE_CTR_LOW").orElseThrow().content());

        store.saveContent("ad-evolve-suggest", "RULE_CTR_LOW", "话术 v2", "ops2");
        assertEquals("话术 v2", store.findEntry("ad-evolve-suggest", "RULE_CTR_LOW").orElseThrow().content());
        assertEquals(1, repository.count(), "同键更新不产生新记录");

        assertTrue(store.isOpsModified("ad-evolve-suggest", "RULE_CTR_LOW"));

        store.saveContent("ad-evolve-suggest", "RULE_CTR_LOW", "种子", JpaSkillTemplateStore.SEED_AUTHOR);
        assertFalse(store.isOpsModified("ad-evolve-suggest", "RULE_CTR_LOW"), "种子行不算运营修改");

        assertEquals(1, store.listEntries().size());

        store.deleteContent("ad-evolve-suggest", "RULE_CTR_LOW");
        assertTrue(store.findEntry("ad-evolve-suggest", "RULE_CTR_LOW").isEmpty());
    }
}
