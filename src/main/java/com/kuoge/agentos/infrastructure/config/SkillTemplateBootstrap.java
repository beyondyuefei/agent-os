package com.kuoge.agentos.infrastructure.config;

import com.kuoge.agentos.infrastructure.persistence.entity.SkillTemplateEntity;
import com.kuoge.agentos.infrastructure.persistence.entity.SkillTemplateVersionEntity;
import com.kuoge.agentos.infrastructure.persistence.repository.SkillTemplateRepository;
import com.kuoge.agentos.infrastructure.persistence.repository.SkillTemplateVersionRepository;
import com.kuoge.agentos.skill.md.SkillContract;
import com.kuoge.agentos.skill.md.SkillContractParser;
import com.kuoge.agentos.skill.md.SkillTemplateStore;
import com.kuoge.agentos.skill.md.TemplateContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Skill 模板种子导入（启动时执行一次）：
 * jar 内 resources/skills/&ast;/contract.yaml 的默认内容是数据库
 * skill_template 表的初始化数据，同时写入版本快照表（v1 / PUBLISHED / 种子）。
 * 运行时唯一数据源 = 数据库。
 * <ul>
 *   <li>幂等：某 skill 已有记录（含运营发布）则跳过，绝不冲掉运营修改</li>
 *   <li>契约与默认内容的变更仍走代码评审（contract.yaml 的 PR），启动时自动入库</li>
 * </ul>
 */
public class SkillTemplateBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SkillTemplateBootstrap.class);
    private static final String SCAN_PATTERN = "classpath*:skills/*/contract.yaml";

    private final SkillTemplateRepository templateRepository;
    private final SkillTemplateVersionRepository versionRepository;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public SkillTemplateBootstrap(SkillTemplateRepository templateRepository,
                                SkillTemplateVersionRepository versionRepository) {
        this.templateRepository = templateRepository;
        this.versionRepository = versionRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        int seeded = 0, skipped = 0;
        try {
            for (Resource resource : resolver.getResources(SCAN_PATTERN)) {
                SkillContract contract = SkillContractParser.parse(
                        resource.getContentAsString(StandardCharsets.UTF_8), resource.getFilename());
                String name = contract.metadata().name();
                if (!templateRepository.findBySkillName(name).isEmpty()) {
                    skipped++;   // 已有种子或运营发布，不动
                    continue;
                }
                for (Map.Entry<String, TemplateContract> t : contract.templates().entrySet()) {
                    seedTemplate(name, t.getKey(), t.getValue().defaultContent());
                }
                seeded++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan skills/ contracts for seeding", e);
        }
        log.info("[SkillTemplateBootstrap] seeded={}, skipped(existing)={}", seeded, skipped);
    }

    private void seedTemplate(String name, String templateKey, String defaultContent) {
        SkillTemplateEntity entity = new SkillTemplateEntity();
        entity.setId(SkillTemplateEntity.buildId(name, templateKey));
        entity.setSkillName(name);
        entity.setTemplateKey(templateKey);
        entity.setContent(defaultContent);
        entity.setUpdatedBy(SkillTemplateStore.SEED_AUTHOR);
        templateRepository.save(entity);

        // 种子同时落版本快照 v1，保证历史表从第一天起完整
        SkillTemplateVersionEntity version = new SkillTemplateVersionEntity();
        version.setSkillName(name);
        version.setTemplateKey(templateKey);
        version.setVersionNo(1);
        version.setContent(defaultContent);
        version.setStatus(SkillTemplateVersionEntity.STATUS_PUBLISHED);
        version.setUpdatedBy(SkillTemplateStore.SEED_AUTHOR);
        version.setReviewRef("seed");
        versionRepository.save(version);
    }
}
