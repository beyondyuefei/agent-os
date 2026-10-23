package com.kuoge.agentos.infrastructure.persistence;

import com.kuoge.agentos.infrastructure.persistence.entity.SkillTemplateEntity;
import com.kuoge.agentos.infrastructure.persistence.repository.SkillTemplateRepository;
import com.kuoge.agentos.skill.md.SkillTemplateStore;

import java.util.List;
import java.util.Optional;

/**
 * 基于数据库的 Skill 模板存储（运行时唯一数据源；
 * 种子由 SkillTemplateBootstrap 在启动时从 contract.yaml 导入）。
 */
public class JpaSkillTemplateStore implements SkillTemplateStore {

    private final SkillTemplateRepository repository;

    public JpaSkillTemplateStore(SkillTemplateRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<TemplateEntry> findEntry(String skillName, String templateKey) {
        return repository.findById(SkillTemplateEntity.buildId(skillName, templateKey))
                .map(e -> new TemplateEntry(e.getSkillName(), e.getTemplateKey(),
                        e.getContent(), e.getUpdatedBy()));
    }

    @Override
    public void saveContent(String skillName, String templateKey, String content, String updatedBy) {
        SkillTemplateEntity entity = repository
                .findById(SkillTemplateEntity.buildId(skillName, templateKey))
                .orElseGet(SkillTemplateEntity::new);
        entity.setId(SkillTemplateEntity.buildId(skillName, templateKey));
        entity.setSkillName(skillName);
        entity.setTemplateKey(templateKey);
        entity.setContent(content);
        entity.setUpdatedBy(updatedBy);
        repository.save(entity);
    }

    @Override
    public void deleteContent(String skillName, String templateKey) {
        repository.deleteById(SkillTemplateEntity.buildId(skillName, templateKey));
    }

    @Override
    public List<String> listEntries() {
        return repository.findAll().stream().map(SkillTemplateEntity::getId).sorted().toList();
    }
}
