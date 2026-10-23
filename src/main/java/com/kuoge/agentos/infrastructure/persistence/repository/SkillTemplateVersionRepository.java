package com.kuoge.agentos.infrastructure.persistence.repository;

import com.kuoge.agentos.infrastructure.persistence.entity.SkillTemplateVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillTemplateVersionRepository extends JpaRepository<SkillTemplateVersionEntity, Long> {

    List<SkillTemplateVersionEntity> findBySkillNameAndTemplateKeyOrderByVersionNoDesc(
            String skillName, String templateKey);

    Optional<SkillTemplateVersionEntity> findFirstBySkillNameAndTemplateKeyAndStatusOrderByVersionNoDesc(
            String skillName, String templateKey, String status);

    Optional<SkillTemplateVersionEntity> findFirstBySkillNameAndTemplateKeyAndStatusAndVersionNo(
            String skillName, String templateKey, String status, int versionNo);
}
