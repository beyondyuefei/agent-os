package com.kuoge.agentos.infrastructure.persistence.ads.repository;

import com.kuoge.agentos.infrastructure.persistence.ads.entity.ExperienceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExperienceRepository extends JpaRepository<ExperienceEntity, Long> {

    /** 生效经验：仅已审核可见（验收规则 9） */
    List<ExperienceEntity> findByApprovedTrueOrderById();
}
