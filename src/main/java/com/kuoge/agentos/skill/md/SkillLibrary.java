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
 * Skill 库（AgentSkills 规范的三级渐进披露在 JVM 侧的落地）：
 * <ol>
 *   <li>metadata 常驻：构造时扫描 classpath:skills/&ast;/SKILL.md，只登记 frontmatter；</li>
 *   <li>body 按需：{@link #load(String)} 首次调用时读正文并缓存；</li>
 *   <li>references 按需：{@link #loadReference(String, String)} 单独加载引用文件。</li>
 * </ol>
 * 提示词模板外部化后，运营调整提示词不需要改 Java 代码、不需要发版逻辑变更。
 */
public class SkillLibrary {

    private static final String SCAN_PATTERN = "classpath*:skills/*/SKILL.md";

    /** name → 元数据（常驻） */
    private final Map<String, SkillMetadata> catalog = new ConcurrentHashMap<>();
    /** name → SKILL.md 资源（body 延迟读取） */
    private final Map<String, Resource> sources = new ConcurrentHashMap<>();
    /** name → 已加载文档（缓存） */
    private final Map<String, SkillDocument> loaded = new ConcurrentHashMap<>();

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public SkillLibrary() {
        scan();
    }

    private void scan() {
        try {
            for (Resource resource : resolver.getResources(SCAN_PATTERN)) {
                String raw = resource.getContentAsString(StandardCharsets.UTF_8);
                SkillMdParser.Parsed parsed = SkillMdParser.parse(raw, resource.getFilename());
                String name = parsed.metadata().name();
                if (catalog.putIfAbsent(name, parsed.metadata()) != null) {
                    throw new IllegalStateException("Duplicate skill name in skills/: " + name);
                }
                sources.put(name, resource);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan skills/ directory", e);
        }
    }

    /** 第一级：全量元数据目录（常驻可见，用于触发判断 / 展示） */
    public List<SkillMetadata> catalog() {
        return Collections.unmodifiableList(new ArrayList<>(catalog.values()));
    }

    public boolean contains(String name) {
        return catalog.containsKey(name);
    }

    /** 第二级：按需加载 body（首次读取，之后命中缓存） */
    public SkillDocument load(String name) {
        return loaded.computeIfAbsent(name, key -> {
            Resource resource = sources.get(key);
            if (resource == null) {
                throw new IllegalArgumentException("Skill not found in library: " + key);
            }
            try {
                SkillMdParser.Parsed parsed = SkillMdParser.parse(
                        resource.getContentAsString(StandardCharsets.UTF_8), key);
                return new SkillDocument(parsed.metadata(), parsed.body(), listReferences(key));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to load skill body: " + key, e);
            }
        });
    }

    /** 第三级：按需加载 references/ 下的引用文件 */
    public String loadReference(String name, String reference) {
        if (reference.contains("/") || reference.contains("..")) {
            throw new IllegalArgumentException("Illegal reference path: " + reference);
        }
        try {
            Resource resource = resolver.getResource(
                    "classpath:skills/" + name + "/references/" + reference);
            if (!resource.exists()) {
                throw new IllegalArgumentException(
                        "Reference not found: skills/" + name + "/references/" + reference);
            }
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load skill reference: " + name + "/" + reference, e);
        }
    }

    /** 便捷方法：加载 body 并按变量渲染 */
    public String render(String name, Map<String, ?> variables) {
        return PromptTemplate.render(load(name).body(), variables);
    }

    private List<String> listReferences(String name) {
        try {
            List<String> refs = new ArrayList<>();
            for (Resource r : resolver.getResources(
                    "classpath*:skills/" + name + "/references/*")) {
                if (r.isReadable() && r.getFilename() != null) {
                    refs.add(r.getFilename());
                }
            }
            return refs;
        } catch (IOException e) {
            return List.of();
        }
    }

    /** 按 name 查元数据 */
    public Optional<SkillMetadata> metadata(String name) {
        return Optional.ofNullable(catalog.get(name));
    }
}
