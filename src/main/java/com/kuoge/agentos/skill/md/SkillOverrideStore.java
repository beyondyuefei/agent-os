package com.kuoge.agentos.skill.md;

import java.util.List;
import java.util.Optional;

/**
 * Skill 内容存储（数据库为运行时唯一数据源；classpath 仅作启动种子与防御兜底）。
 * 种子行 updatedBy = {@link #SEED_AUTHOR}，运营修改行 updatedBy 为操作人，
 * 后台据此区分"默认值"与"运营已修改"。
 */
public interface SkillOverrideStore {

    /** 种子行的 updatedBy 标识 */
    String SEED_AUTHOR = "seed:classpath";

    /** 存储记录（含审计字段） */
    record OverrideEntry(String skillName, String reference, String content, String updatedBy) {}

    /**
     * 查询记录。
     *
     * @param skillName 模板名（skills/ 目录下文件夹名）
     * @param reference null 或空串 = SKILL.md body；否则为 references/ 下文件名
     */
    Optional<OverrideEntry> findEntry(String skillName, String reference);

    /** 便捷方法：仅取内容 */
    default Optional<String> findContent(String skillName, String reference) {
        return findEntry(skillName, reference).map(OverrideEntry::content);
    }

    /** 保存 / 更新内容（运营修改或种子导入） */
    void saveContent(String skillName, String reference, String content, String updatedBy);

    /** 删除记录（运营回退 → 运行时兜底读 classpath，内容与种子一致） */
    void deleteContent(String skillName, String reference);

    /** 记录清单：skillName（body）或 skillName/reference */
    List<String> listOverrides();

    /** 是否运营修改过（非种子行） */
    default boolean isOpsModified(String skillName, String reference) {
        return findEntry(skillName, reference)
                .map(e -> !SEED_AUTHOR.equals(e.updatedBy()))
                .orElse(false);
    }
}
