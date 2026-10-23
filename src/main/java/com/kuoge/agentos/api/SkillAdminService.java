package com.kuoge.agentos.api;

import com.kuoge.agentos.infrastructure.persistence.entity.SkillTemplateVersionEntity;
import com.kuoge.agentos.infrastructure.persistence.repository.SkillTemplateVersionRepository;
import com.kuoge.agentos.skill.md.PromptTemplate;
import com.kuoge.agentos.skill.md.SkillContract;
import com.kuoge.agentos.skill.md.SkillLibrary;
import com.kuoge.agentos.skill.md.SkillMetadata;
import com.kuoge.agentos.skill.md.SkillTemplateStore;
import com.kuoge.agentos.skill.md.TemplateContract;
import com.kuoge.agentos.skill.md.VariableContract;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Skill 模板管理（运营后台服务端逻辑）。
 * 内容迭代闭环：草稿（DRAFT）→ 发布（PUBLISHED，必填评审单号）→ 快照留存 → 可回滚。
 * 契约防线：保存草稿时校验内容引用的变量必须在 contract.yaml 中声明，
 * 未声明变量当场拒绝——把"运行时渲染失败"前置为"保存时拦截"。
 */
public class SkillAdminService {

    /** 模板视图：元数据 + 运营修改状态 */
    public record SkillView(SkillMetadata metadata, List<String> modifiedTemplates) {}

    /** 模板详情：键 + 变量契约 + 生效内容来源 + 是否有待发布草稿 */
    public record TemplateView(String key, Map<String, VariableContract> variables,
                               String source, boolean hasDraft) {}

    /** 详情视图：元数据 + 全部模板 */
    public record SkillDetailView(SkillMetadata metadata, List<TemplateView> templates) {}

    /** 内容视图：内容 + 来源（SEED / DB_OPS / CONTRACT_DEFAULT） */
    public record ContentView(String content, String source) {}

    /** 版本快照视图 */
    public record VersionView(int versionNo, String status, String updatedBy,
                              String reviewRef, Instant createdAt, String contentPreview) {}

    private final SkillLibrary library;
    private final SkillTemplateStore store;
    private final SkillTemplateVersionRepository versionRepository;

    public SkillAdminService(SkillLibrary library, SkillTemplateStore store,
                             SkillTemplateVersionRepository versionRepository) {
        this.library = library;
        this.store = store;
        this.versionRepository = versionRepository;
    }

    public List<SkillView> list() {
        return library.catalog().stream()
                .map(meta -> new SkillView(meta, modifiedTemplates(meta.name())))
                .toList();
    }

    /** 详情：元数据 + 模板清单（含变量契约与草稿状态，供编辑器展示与校验提示） */
    public SkillDetailView detail(String name) {
        SkillContract contract = library.contract(name);
        List<TemplateView> templates = contract.templates().entrySet().stream()
                .map(e -> new TemplateView(e.getKey(), e.getValue().variables(),
                        sourceOf(name, e.getKey()),
                        latestDraft(name, e.getKey()).isPresent()))
                .toList();
        return new SkillDetailView(contract.metadata(), templates);
    }

    private List<String> modifiedTemplates(String name) {
        return store.listEntries().stream()
                .filter(id -> id.startsWith(name + "/"))
                .map(id -> id.substring(name.length() + 1))
                .filter(key -> store.isOpsModified(name, key))
                .toList();
    }

    private String sourceOf(String name, String templateKey) {
        Optional<SkillTemplateStore.TemplateEntry> entry = store.findEntry(name, templateKey);
        if (entry.isEmpty()) {
            return "CONTRACT_DEFAULT";
        }
        return SkillTemplateStore.SEED_AUTHOR.equals(entry.get().updatedBy()) ? "SEED" : "DB_OPS";
    }

    /** 读取生效内容；source ∈ SEED（默认值）/ DB_OPS（运营发布）/ CONTRACT_DEFAULT（无记录兜底） */
    public ContentView effectiveContent(String name, String templateKey) {
        return new ContentView(library.content(name, templateKey), sourceOf(name, templateKey));
    }

    /** 读取最新草稿（无草稿时返回生效内容，供编辑器初始化） */
    public ContentView draftContent(String name, String templateKey) {
        return latestDraft(name, templateKey)
                .map(d -> new ContentView(d.getContent(), "DRAFT"))
                .orElseGet(() -> effectiveContent(name, templateKey));
    }

    /**
     * 保存草稿：契约校验 —— 内容引用的变量必须全部在 contract.yaml 中声明。
     */
    public void saveDraft(String name, String templateKey, String content, String updatedBy) {
        TemplateContract contract = library.contract(name).requireTemplate(templateKey);
        Set<String> used = PromptTemplate.extractVariables(content);
        List<String> undeclared = used.stream()
                .filter(v -> !contract.variables().containsKey(v))
                .toList();
        if (!undeclared.isEmpty()) {
            throw new IllegalArgumentException(
                    "模板引用了未在契约中声明的变量 " + undeclared
                            + "（契约已声明：" + contract.variables().keySet() + "），需工程侧发布支持");
        }
        // 同模板只保留一条草稿：新草稿覆盖旧草稿
        latestDraft(name, templateKey).ifPresent(old -> versionRepository.deleteById(old.getId()));
        SkillTemplateVersionEntity draft = new SkillTemplateVersionEntity();
        draft.setSkillName(name);
        draft.setTemplateKey(templateKey);
        draft.setVersionNo(nextVersionNo(name, templateKey));
        draft.setContent(content);
        draft.setStatus(SkillTemplateVersionEntity.STATUS_DRAFT);
        draft.setUpdatedBy(updatedBy);
        versionRepository.save(draft);
    }

    /** 发布最新草稿：必填评审单号；写入生效表 + 快照转 PUBLISHED */
    public void publish(String name, String templateKey, String reviewRef, String updatedBy) {
        if (reviewRef == null || reviewRef.isBlank()) {
            throw new IllegalArgumentException("发布必须填写评审单号（reviewRef）");
        }
        SkillTemplateVersionEntity draft = latestDraft(name, templateKey)
                .orElseThrow(() -> new IllegalStateException(
                        "没有待发布的草稿: " + name + "/" + templateKey));
        draft.setStatus(SkillTemplateVersionEntity.STATUS_PUBLISHED);
        draft.setReviewRef(reviewRef);
        draft.setUpdatedBy(updatedBy);
        versionRepository.save(draft);
        store.saveContent(name, templateKey, draft.getContent(), updatedBy);
    }

    /** 丢弃草稿 */
    public void discardDraft(String name, String templateKey) {
        latestDraft(name, templateKey).ifPresent(d -> versionRepository.deleteById(d.getId()));
    }

    /** 版本历史（新→旧） */
    public List<VersionView> history(String name, String templateKey) {
        return versionRepository
                .findBySkillNameAndTemplateKeyOrderByVersionNoDesc(name, templateKey)
                .stream()
                .map(v -> new VersionView(v.getVersionNo(), v.getStatus(), v.getUpdatedBy(),
                        v.getReviewRef(), v.getCreatedAt(),
                        v.getContent().length() > 60
                                ? v.getContent().substring(0, 60) + "…" : v.getContent()))
                .toList();
    }

    /** 回滚：以历史 PUBLISHED 内容为内容发布新版本（同样要求评审单号） */
    public void rollback(String name, String templateKey, int versionNo,
                         String reviewRef, String updatedBy) {
        if (reviewRef == null || reviewRef.isBlank()) {
            throw new IllegalArgumentException("回滚必须填写评审单号（reviewRef）");
        }
        SkillTemplateVersionEntity target = versionRepository
                .findFirstBySkillNameAndTemplateKeyAndStatusAndVersionNo(
                        name, templateKey, SkillTemplateVersionEntity.STATUS_PUBLISHED, versionNo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "历史版本不存在: " + name + "/" + templateKey + "#v" + versionNo));
        // 回滚 = 以旧内容产生新的 PUBLISHED 版本（历史不被改写，审计链完整）
        SkillTemplateVersionEntity rollback = new SkillTemplateVersionEntity();
        rollback.setSkillName(name);
        rollback.setTemplateKey(templateKey);
        rollback.setVersionNo(nextVersionNo(name, templateKey));
        rollback.setContent(target.getContent());
        rollback.setStatus(SkillTemplateVersionEntity.STATUS_PUBLISHED);
        rollback.setUpdatedBy(updatedBy);
        rollback.setReviewRef(reviewRef + " (rollback of v" + versionNo + ")");
        versionRepository.save(rollback);
        store.saveContent(name, templateKey, target.getContent(), updatedBy);
    }

    private Optional<SkillTemplateVersionEntity> latestDraft(String name, String templateKey) {
        return versionRepository.findFirstBySkillNameAndTemplateKeyAndStatusOrderByVersionNoDesc(
                name, templateKey, SkillTemplateVersionEntity.STATUS_DRAFT);
    }

    private int nextVersionNo(String name, String templateKey) {
        return versionRepository
                .findBySkillNameAndTemplateKeyOrderByVersionNoDesc(name, templateKey)
                .stream().mapToInt(SkillTemplateVersionEntity::getVersionNo)
                .max().orElse(0) + 1;
    }
}
