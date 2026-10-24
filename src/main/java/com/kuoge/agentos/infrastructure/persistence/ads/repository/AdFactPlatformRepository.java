package com.kuoge.agentos.infrastructure.persistence.ads.repository;

import com.kuoge.agentos.infrastructure.persistence.ads.entity.AdFactPlatformEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdFactPlatformRepository extends JpaRepository<AdFactPlatformEntity, Long> {

    List<AdFactPlatformEntity> findByPlatformAdIdOrderById(String platformAdId);
}
