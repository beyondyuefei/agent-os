package com.kuoge.agentos.infrastructure.persistence;

import com.kuoge.agentos.infrastructure.persistence.repository.SkillTemplateRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JPA 覆盖存储测试（H2 内存库）：落库 / 更新 / 删除 / 清单。
 */
@DataJpaTest
class JpaSkillOverrideStoreTest {

    @Autowired
    private SkillTemplateRepository repository;

    @Test
    void saveUpdateDeleteRoundTrip() {
        JpaSkillOverrideStore store = new JpaSkillOverrideStore(repository);

        store.saveContent("ad-evolve-suggest", "RULE_CTR_LOW.md", "话术 v1", "ops");
        Optional<String> found = store.findContent("ad-evolve-suggest", "RULE_CTR_LOW.md");
        assertTrue(found.isPresent());
        assertEquals("话术 v1", found.get());

        // 更新同键
        store.saveContent("ad-evolve-suggest", "RULE_CTR_LOW.md", "话术 v2", "ops2");
        assertEquals("话术 v2", store.findContent("ad-evolve-suggest", "RULE_CTR_LOW.md").get());
        assertEquals(1, repository.count(), "同键更新不产生新记录");

        // body 与 reference 是不同记录
        store.saveContent("ad-evolve-suggest", null, "body 内容", "ops");
        assertEquals(2, repository.count());
        assertEquals("body 内容", store.findContent("ad-evolve-suggest", null).get());

        assertEquals(2, store.listOverrides().size());

        store.deleteContent("ad-evolve-suggest", "RULE_CTR_LOW.md");
        assertTrue(store.findContent("ad-evolve-suggest", "RULE_CTR_LOW.md").isEmpty());
        assertEquals(1, repository.count());
    }
}
