package com.kuoge.agentos.infrastructure.persistence.ads.repository;

import com.kuoge.agentos.infrastructure.persistence.ads.entity.PlanDefEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanDefRepository extends JpaRepository<PlanDefEntity, String> {
}
