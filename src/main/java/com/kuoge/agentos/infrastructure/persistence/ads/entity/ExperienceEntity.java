package com.kuoge.agentos.infrastructure.persistence.ads.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 经验候选库表（experience）：字段展开存储。
 * 与内存实现一致的追加语义：approve 产生同 exp_id 的已审核新行，
 * 生效判定以 approved=true 为准（验收规则 9）。
 */
@Entity
@Table(name = "experience", indexes = @Index(name = "idx_experience_exp_id", columnList = "exp_id"))
@Data
public class ExperienceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 经验条目业务 id（exp_xxx） */
    @Column(name = "exp_id", length = 64, nullable = false)
    private String expId;

    @Lob
    @Column(nullable = false)
    private String rule;

    @Column(length = 128)
    private String source;

    @Column(name = "applicable_condition", length = 512)
    private String applicableCondition;

    @Column(nullable = false)
    private boolean approved;
}
