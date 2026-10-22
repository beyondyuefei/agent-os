package com.kuoge.agentos.skill.md;

import java.util.List;
import java.util.Optional;

/**
 * Skill 内容覆盖存储（优先级高于 classpath 默认值）。
 * 运营在管理后台修改的模板落库后，经此接口被 {@link SkillLibrary} 优先读取；
 * 删除覆盖记录即回退到 jar 内随版本发布的默认模板。
 */
public interface SkillOverrideStore {

    /**
     * 查询覆盖内容。
     *
     * @param skillName 模板名（skills/ 目录下文件夹名）
     * @param reference null 或空串 = SKILL.md body；否则为 references/ 下文件名
     */
    Optional<String> findContent(String skillName, String reference);

    /** 保存 / 更新覆盖内容 */
    void saveContent(String skillName, String reference, String content, String updatedBy);

    /** 删除覆盖（回退 classpath 默认） */
    void deleteContent(String skillName, String reference);

    /** 覆盖记录清单：skillName 或 skillName/reference */
    List<String> listOverrides();
}
