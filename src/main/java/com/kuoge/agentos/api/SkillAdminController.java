package com.kuoge.agentos.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Skill 模板运营后台接口：
 * 运营查看 / 修改 SKILL.md 提示词与话术模板，保存即生效（数据库覆盖优先于
 * classpath 默认模板），删除覆盖即回退默认。
 */
@RestController
@RequestMapping("/api/admin/skills")
public class SkillAdminController {

    private final SkillAdminService service;

    public SkillAdminController(SkillAdminService service) {
        this.service = service;
    }

    /** 模板清单：元数据 + 覆盖状态 */
    @GetMapping
    public List<SkillAdminService.SkillView> list() {
        return service.list();
    }

    /** 读取生效内容；ref 为空 = SKILL.md body，否则为 references/ 文件名 */
    @GetMapping("/{name}/content")
    public SkillAdminService.ContentView content(@PathVariable String name,
                                                 @RequestParam(required = false) String ref) {
        return service.effectiveContent(name, ref);
    }

    /** 保存覆盖（body 为模板全文；保存即生效） */
    @PutMapping("/{name}/content")
    public ResponseEntity<Void> save(@PathVariable String name,
                                     @RequestParam(required = false) String ref,
                                     @RequestParam(defaultValue = "admin") String updatedBy,
                                     @RequestBody String content) {
        service.saveOverride(name, ref, content, updatedBy);
        return ResponseEntity.noContent().build();
    }

    /** 删除覆盖 → 回退 classpath 默认模板 */
    @DeleteMapping("/{name}/content")
    public ResponseEntity<Void> revert(@PathVariable String name,
                                       @RequestParam(required = false) String ref) {
        service.revert(name, ref);
        return ResponseEntity.noContent().build();
    }
}
