package com.kuoge.agentos.infrastructure.persistence.ads.repository;

import com.kuoge.agentos.infrastructure.persistence.ads.entity.QuotaProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuotaProfileRepository extends JpaRepository<QuotaProfileEntity, String> {
}
