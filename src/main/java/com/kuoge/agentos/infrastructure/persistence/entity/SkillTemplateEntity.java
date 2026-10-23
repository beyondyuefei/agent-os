package com.kuoge.agentos.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Skill 模板当前生效内容表：运行时读取的唯一数据源。
 * 主键格式：skillName/templateKey。
 * 种子由 SkillTemplateBootstrap 从 contract.yaml 导入；运营发布经版本快照表审计后写入。
 */
@Entity
@Table(name = "skill_template")
@Data
public class SkillTemplateEntity {

    @Id
    @Column(length = 160)
    private String id;

    @Column(length = 96, nullable = false)
    private String skillName;

    /** contract.yaml templates 下声明的模板键 */
    @Column(length = 64, nullable = false)
    private String templateKey;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(length = 64)
    private String updatedBy;

    @UpdateTimestamp
    private Instant updatedAt;

    public static String buildId(String skillName, String templateKey) {
        return skillName + "/" + templateKey;
    }
}
