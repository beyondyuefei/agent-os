package com.kuoge.agentos.api;

import com.kuoge.agentos.skill.md.SkillLibrary;
import com.kuoge.agentos.skill.md.SkillMdParser;
import com.kuoge.agentos.skill.md.SkillMetadata;
import com.kuoge.agentos.skill.md.SkillOverrideStore;

import java.util.List;
import java.util.Optional;

/**
 * Skill 模板管理（运营后台服务端逻辑）：
 * 覆盖内容落库优先于 classpath 默认模板；保存 body 时做 frontmatter 校验，
 * 防止运营保存格式错误的模板导致运行时渲染失败。
 */
public class SkillAdminService {

    /** 模板视图：元数据 + 是否存在数据库覆盖 */
    public record SkillView(SkillMetadata metadata, boolean bodyOverridden,
                            List<String> overriddenReferences) {}

    /** 内容视图：生效内容 + 来源（DB / CLASSPATH） */
    public record ContentView(String content, String source) {}

    private final SkillLibrary library;
    private final SkillOverrideStore overrideStore;

    public SkillAdminService(SkillLibrary library, SkillOverrideStore overrideStore) {
        this.library = library;
        this.overrideStore = overrideStore;
    }

    public List<SkillView> list() {
        return library.catalog().stream()
                .map(meta -> new SkillView(meta,
                        overrideStore.findContent(meta.name(), null).isPresent(),
                        overrideStore.listOverrides().stream()
                                .filter(id -> id.startsWith(meta.name() + "/"))
                                .map(id -> id.substring(meta.name().length() + 1))
                                .toList()))
                .toList();
    }

    /** 读取生效内容（覆盖优先），source 标明来自 DB 还是 CLASSPATH；body 统一返回解析后的正文 */
    public ContentView effectiveContent(String name, String reference) {
        boolean isBody = reference == null || reference.isBlank();
        Optional<String> overridden = overrideStore.findContent(name, reference);
        if (overridden.isPresent()) {
            String content = overridden.get();
            return new ContentView(
                    isBody ? SkillMdParser.parse(content, name + "(override)").body() : content,
                    "DB");
        }
        String content = isBody
                ? library.load(name).body()
                : library.loadReference(name, reference);
        return new ContentView(content, "CLASSPATH");
    }

    /** 保存覆盖：body（ref 为空）必须是可解析的 SKILL.md 格式 */
    public void saveOverride(String name, String reference, String content, String updatedBy) {
        if (reference == null || reference.isBlank()) {
            SkillMdParser.parse(content, name + "(admin)");   // 校验失败即拒绝保存
        }
        overrideStore.saveContent(name, reference, content, updatedBy);
        library.invalidate(name);
    }

    /** 删除覆盖 → 回退 classpath 默认模板 */
    public void revert(String name, String reference) {
        overrideStore.deleteContent(name, reference);
        library.invalidate(name);
    }
}
