package com.kuoge.agentos.infrastructure.persistence.repository;

import com.kuoge.agentos.infrastructure.persistence.entity.SkillTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SkillTemplateRepository extends JpaRepository<SkillTemplateEntity, String> {

    List<SkillTemplateEntity> findBySkillName(String skillName);
}
