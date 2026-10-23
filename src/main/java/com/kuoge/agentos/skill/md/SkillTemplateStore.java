package com.kuoge.agentos.skill.md;

import java.util.List;
import java.util.Optional;

/**
 * Skill 模板存储（数据库为运行时唯一数据源；contract.yaml 仅作启动种子与契约锚点）。
 * 种子行 updatedBy = {@link #SEED_AUTHOR}，运营发布行 updatedBy 为操作人，
 * 后台据此区分"默认值"与"运营已发布"。
 */
public interface SkillTemplateStore {

    /** 种子行的 updatedBy 标识 */
    String SEED_AUTHOR = "seed:contract";

    /** 存储记录（含审计字段）；内容为纯模板文本（不含契约元数据） */
    record TemplateEntry(String skillName, String templateKey, String content, String updatedBy) {}

    /**
     * 查询当前生效内容记录。
     *
     * @param skillName   模板名（skills/ 目录下文件夹名）
     * @param templateKey 模板键（contract.yaml templates 下声明的键）
     */
    Optional<TemplateEntry> findEntry(String skillName, String templateKey);

    /** 保存 / 更新生效内容（种子导入或运营发布） */
    void saveContent(String skillName, String templateKey, String content, String updatedBy);

    /** 删除记录（回退 → 运行时读 contract.yaml 默认值） */
    void deleteContent(String skillName, String templateKey);

    /** 记录清单：skillName/templateKey */
    List<String> listEntries();

    /** 是否运营发布过（非种子行） */
    default boolean isOpsModified(String skillName, String templateKey) {
        return findEntry(skillName, templateKey)
                .map(e -> !SEED_AUTHOR.equals(e.updatedBy()))
                .orElse(false);
    }
}
