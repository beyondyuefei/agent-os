package com.kuoge.agentos.infrastructure.config;

import com.kuoge.agentos.infrastructure.persistence.entity.SkillTemplateEntity;
import com.kuoge.agentos.infrastructure.persistence.repository.SkillTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Skill 模板种子导入（启动时执行一次）：
 * jar 内 resources/skills/ 的 md 模板不再作为运行时数据源，而是数据库
 * skill_template 表的初始化数据。运行时唯一数据源 = 数据库。
 * <ul>
 *   <li>幂等：某 skill 已有记录（含运营覆盖）则跳过，绝不冲掉运营修改</li>
 *   <li>新模板准入仍走代码评审（随版本发布的 md 文件），启动时自动入库</li>
 * </ul>
 */
public class SkillTemplateBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SkillTemplateBootstrap.class);
    private static final String SCAN_PATTERN = "classpath*:skills/*/SKILL.md";

    private final SkillTemplateRepository repository;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public SkillTemplateBootstrap(SkillTemplateRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        int seeded = 0, skipped = 0;
        for (Map.Entry<String, Resource> entry : scanSkillBodies().entrySet()) {
            String name = entry.getKey();
            if (!repository.findBySkillName(name).isEmpty()) {
                skipped++;   // 已有种子或运营覆盖，不动
                continue;
            }
            try {
                saveSeed(name, null, entry.getValue());
                for (Resource ref : resolver.getResources(
                        "classpath*:skills/" + name + "/references/*")) {
                    if (ref.isReadable() && ref.getFilename() != null) {
                        saveSeed(name, ref.getFilename(), ref);
                    }
                }
                seeded++;
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to seed skill template: " + name, e);
            }
        }
        log.info("[SkillTemplateBootstrap] seeded={}, skipped(existing)={}", seeded, skipped);
    }

    private Map<String, Resource> scanSkillBodies() {
        try {
            Map<String, Resource> result = new LinkedHashMap<>();
            for (Resource resource : resolver.getResources(SCAN_PATTERN)) {
                // URL 形如 .../skills/<name>/SKILL.md
                String path = resource.getURI().toString();
                int skillsIdx = path.indexOf("/skills/");
                if (skillsIdx < 0) continue;
                String rest = path.substring(skillsIdx + "/skills/".length());
                String name = rest.substring(0, rest.indexOf('/'));
                result.put(name, resource);
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan skills/ for seeding", e);
        }
    }

    private void saveSeed(String name, String reference, Resource resource) throws IOException {
        SkillTemplateEntity entity = new SkillTemplateEntity();
        entity.setId(SkillTemplateEntity.buildId(name, reference));
        entity.setSkillName(name);
        entity.setReferenceKey(reference);
        entity.setContent(resource.getContentAsString(StandardCharsets.UTF_8));
        entity.setUpdatedBy("seed:classpath");
        repository.save(entity);
    }
}
