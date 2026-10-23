package com.kuoge.agentos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Skill 模板版本快照表：内容迭代的版本化与评审载体。
 * 每次保存草稿产生一条 DRAFT；发布时转为 PUBLISHED 并写入生效表；
 * 回滚 = 以历史 PUBLISHED 内容为内容产生新的 PUBLISHED 版本。
 */
@Entity
@Table(name = "skill_template_version")
@Data
public class SkillTemplateVersionEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 96, nullable = false)
    private String skillName;

    @Column(length = 64, nullable = false)
    private String templateKey;

    /** 单调递增版本号（按 skillName + templateKey 维度） */
    @Column(nullable = false)
    private int versionNo;

    @Lob
    @Column(nullable = false)
    private String content;

    /** DRAFT | PUBLISHED */
    @Column(length = 16, nullable = false)
    private String status;

    @Column(length = 64)
    private String updatedBy;

    /** 评审单号（轻量评审流：发布时必填，后续可对接飞书审批自动化） */
    @Column(length = 128)
    private String reviewRef;

    @CreationTimestamp
    private Instant createdAt;
}
