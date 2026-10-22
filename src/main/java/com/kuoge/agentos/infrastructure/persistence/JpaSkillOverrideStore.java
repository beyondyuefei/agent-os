package com.kuoge.agentos.infrastructure.persistence;

import com.kuoge.agentos.infrastructure.persistence.entity.SkillTemplateEntity;
import com.kuoge.agentos.infrastructure.persistence.repository.SkillTemplateRepository;
import com.kuoge.agentos.skill.md.SkillOverrideStore;

import java.util.List;
import java.util.Optional;

/**
 * 基于数据库的 Skill 覆盖存储（运营后台写入，运行时优先于 classpath 读取）。
 */
public class JpaSkillOverrideStore implements SkillOverrideStore {

    private final SkillTemplateRepository repository;

    public JpaSkillOverrideStore(SkillTemplateRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<String> findContent(String skillName, String reference) {
        return repository.findById(SkillTemplateEntity.buildId(skillName, reference))
                .map(SkillTemplateEntity::getContent);
    }

    @Override
    public void saveContent(String skillName, String reference, String content, String updatedBy) {
        SkillTemplateEntity entity = repository
                .findById(SkillTemplateEntity.buildId(skillName, reference))
                .orElseGet(SkillTemplateEntity::new);
        entity.setId(SkillTemplateEntity.buildId(skillName, reference));
        entity.setSkillName(skillName);
        entity.setReferenceKey(reference == null || reference.isBlank() ? null : reference);
        entity.setContent(content);
        entity.setUpdatedBy(updatedBy);
        repository.save(entity);
    }

    @Override
    public void deleteContent(String skillName, String reference) {
        repository.deleteById(SkillTemplateEntity.buildId(skillName, reference));
    }

    @Override
    public List<String> listOverrides() {
        return repository.findAll().stream().map(SkillTemplateEntity::getId).sorted().toList();
    }
}
