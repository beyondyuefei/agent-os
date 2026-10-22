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
 * Skill 模板覆盖表：运营在管理后台修改的 SKILL.md body / references 内容。
 * 存在记录 = 覆盖生效；删除记录 = 回退 classpath 默认模板。
 * 主键格式：skillName（body）或 skillName/reference（引用文件）。
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

    /** null / 空串 = SKILL.md body；否则为 references/ 下文件名 */
    @Column(length = 64)
    private String referenceKey;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(length = 64)
    private String updatedBy;

    @UpdateTimestamp
    private Instant updatedAt;

    public static String buildId(String skillName, String reference) {
        return reference == null || reference.isBlank()
                ? skillName : skillName + "/" + reference;
    }
}
