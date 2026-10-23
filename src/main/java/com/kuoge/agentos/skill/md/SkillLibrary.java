package com.kuoge.agentos.skill.md;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skill 库（契约驱动版）。
 * 结构对齐 AgentSkills 规范的三级渐进披露，语义为业务系统改写：
 * <ol>
 *   <li>contract 常驻：扫描 classpath:skills/&ast;/contract.yaml，元数据 + 变量契约常驻；</li>
 *   <li>内容按需：{@link #content(String, String)} 优先读数据库生效内容，缺省回退契约默认值；</li>
 *   <li>渲染即用：{@link #render(String, String, Map)} 模板渲染，缺变量 fail-fast。</li>
 * </ol>
 * 契约（变量声明、模板集合）只能随版本评审变更；内容迭代走运营端草稿 / 发布流。
 */
public class SkillLibrary {

    private static final String SCAN_PATTERN = "classpath*:skills/*/contract.yaml";

    /** name → 契约（常驻） */
    private final Map<String, SkillContract> contracts = new ConcurrentHashMap<>();
    /** 生效内容存储（可为 null = 仅用契约默认值） */
    private final SkillTemplateStore store;

    public SkillLibrary() {
        this(null);
    }

    public SkillLibrary(SkillTemplateStore store) {
        this.store = store;
        scan();
    }

    private void scan() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            for (Resource resource : resolver.getResources(SCAN_PATTERN)) {
                SkillContract contract = SkillContractParser.parse(
                        resource.getContentAsString(StandardCharsets.UTF_8), resource.getFilename());
                String name = contract.metadata().name();
                if (contracts.putIfAbsent(name, contract) != null) {
                    throw new IllegalStateException("Duplicate skill contract in skills/: " + name);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan skills/ contracts", e);
        }
    }

    /** 全量元数据目录（常驻可见） */
    public List<SkillMetadata> catalog() {
        List<SkillMetadata> result = new ArrayList<>();
        for (SkillContract c : contracts.values()) {
            result.add(c.metadata());
        }
        return Collections.unmodifiableList(result);
    }

    public boolean contains(String name) {
        return contracts.containsKey(name);
    }

    public Optional<SkillMetadata> metadata(String name) {
        SkillContract contract = contracts.get(name);
        return contract == null ? Optional.empty() : Optional.of(contract.metadata());
    }

    /** 契约查询（后台展示变量声明 / 保存时校验用） */
    public SkillContract contract(String name) {
        SkillContract contract = contracts.get(name);
        if (contract == null) {
            throw new IllegalArgumentException("Skill contract not found: " + name);
        }
        return contract;
    }

    /** 生效内容：数据库发布内容优先，缺省回退契约默认值 */
    public String content(String name, String templateKey) {
        TemplateContract template = contracts.containsKey(name)
                ? contracts.get(name).requireTemplate(templateKey)
                : null;
        if (store != null) {
            Optional<SkillTemplateStore.TemplateEntry> entry = store.findEntry(name, templateKey);
            if (entry.isPresent()) {
                return entry.get().content();
            }
        }
        if (template == null) {
            throw new IllegalArgumentException("Skill contract not found: " + name);
        }
        return template.defaultContent();
    }

    /** 渲染：生效内容 + 变量注入；变量缺失 fail-fast */
    public String render(String name, String templateKey, Map<String, ?> variables) {
        return PromptTemplate.render(content(name, templateKey), variables);
    }

    /** 存储是否已接入 */
    public boolean storePresent() {
        return store != null;
    }
}
