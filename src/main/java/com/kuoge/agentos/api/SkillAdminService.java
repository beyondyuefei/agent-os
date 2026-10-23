package com.kuoge.agentos.api;

import com.kuoge.agentos.skill.md.SkillLibrary;
import com.kuoge.agentos.skill.md.SkillMdParser;
import com.kuoge.agentos.skill.md.SkillMetadata;
import com.kuoge.agentos.skill.md.SkillOverrideStore;

import java.util.List;
import java.util.Optional;

/**
 * Skill 模板管理（运营后台服务端逻辑）。
 * 数据库是运行时唯一数据源（启动时从 classpath 种子导入）；后台据此区分：
 * SEED = 种子默认值（随版本评审发布）；DB_OPS = 运营修改；CLASSPATH = 无记录兜底。
 * 保存 body 时做 frontmatter 校验，防止格式错误的模板导致运行时渲染失败。
 */
public class SkillAdminService {

    /** 模板视图：元数据 + 运营修改状态 */
    public record SkillView(SkillMetadata metadata, boolean bodyModified,
                            List<String> modifiedReferences) {}

    /** 详情视图：模板元数据 + 可用引用文件清单（供编辑器切换） */
    public record SkillDetailView(SkillMetadata metadata, List<String> references,
                                  boolean bodyModified, List<String> modifiedReferences) {}

    /** 内容视图：生效内容 + 来源（SEED / DB_OPS / CLASSPATH） */
    public record ContentView(String content, String source) {}

    private final SkillLibrary library;
    private final SkillOverrideStore store;

    public SkillAdminService(SkillLibrary library, SkillOverrideStore store) {
        this.library = library;
        this.store = store;
    }

    public List<SkillView> list() {
        return library.catalog().stream()
                .map(meta -> new SkillView(meta,
                        store.isOpsModified(meta.name(), null),
                        modifiedReferences(meta.name())))
                .toList();
    }

    /** 详情：元数据 + 全部可用引用文件 + 修改状态 */
    public SkillDetailView detail(String name) {
        SkillMetadata meta = library.metadata(name)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + name));
        return new SkillDetailView(meta, library.load(name).references(),
                store.isOpsModified(name, null), modifiedReferences(name));
    }

    private List<String> modifiedReferences(String name) {
        return store.listOverrides().stream()
                .filter(id -> id.startsWith(name + "/"))
                .map(id -> id.substring(name.length() + 1))
                .filter(ref -> store.isOpsModified(name, ref))
                .toList();
    }

    /** 读取生效内容；source ∈ SEED（默认值）/ DB_OPS（运营改过）/ CLASSPATH（无记录兜底）；body 统一返回解析后正文 */
    public ContentView effectiveContent(String name, String reference) {
        boolean isBody = reference == null || reference.isBlank();
        Optional<SkillOverrideStore.OverrideEntry> entry = store.findEntry(name, reference);
        if (entry.isPresent()) {
            String content = entry.get().content();
            return new ContentView(
                    isBody ? SkillMdParser.parse(content, name + "(store)").body() : content,
                    SkillOverrideStore.SEED_AUTHOR.equals(entry.get().updatedBy()) ? "SEED" : "DB_OPS");
        }
        String content = isBody
                ? library.load(name).body()
                : library.loadReference(name, reference);
        return new ContentView(content, "CLASSPATH");
    }

    /** 保存：body（ref 为空）必须是可解析的 SKILL.md 格式 */
    public void saveOverride(String name, String reference, String content, String updatedBy) {
        if (reference == null || reference.isBlank()) {
            SkillMdParser.parse(content, name + "(admin)");   // 校验失败即拒绝保存
        }
        store.saveContent(name, reference, content, updatedBy);
        library.invalidate(name);
    }

    /** 删除记录 → 兜底读 classpath（内容与种子一致，即回退默认） */
    public void revert(String name, String reference) {
        store.deleteContent(name, reference);
        library.invalidate(name);
    }
}
