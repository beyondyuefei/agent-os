package com.kuoge.agentos.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Skill 模板运营后台接口：草稿 / 发布 / 历史 / 回滚的内容迭代闭环。
 * 数据库为运行时唯一数据源；契约（contract.yaml）随版本评审，内容经评审流发布。
 */
@RestController
@RequestMapping("/api/admin/skills")
public class SkillAdminController {

    private final SkillAdminService service;

    public SkillAdminController(SkillAdminService service) {
        this.service = service;
    }

    /** 模板清单：元数据 + 运营修改状态 */
    @GetMapping
    public List<SkillAdminService.SkillView> list() {
        return service.list();
    }

    /** 详情：元数据 + 模板清单（含变量契约与草稿状态） */
    @GetMapping("/{name}")
    public SkillAdminService.SkillDetailView detail(@PathVariable String name) {
        return service.detail(name);
    }

    /** 生效内容 */
    @GetMapping("/{name}/templates/{key}/content")
    public SkillAdminService.ContentView content(@PathVariable String name, @PathVariable String key) {
        return service.effectiveContent(name, key);
    }

    /** 草稿内容（无草稿时返回生效内容，供编辑器初始化） */
    @GetMapping("/{name}/templates/{key}/draft")
    public SkillAdminService.ContentView draft(@PathVariable String name, @PathVariable String key) {
        return service.draftContent(name, key);
    }

    /** 保存草稿（契约校验：未声明变量当场拒绝） */
    @PutMapping("/{name}/templates/{key}/draft")
    public ResponseEntity<?> saveDraft(@PathVariable String name, @PathVariable String key,
                                       @RequestParam(defaultValue = "ops-panel") String updatedBy,
                                       @RequestBody String content) {
        try {
            service.saveDraft(name, key, content, updatedBy);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 发布草稿（必填评审单号） */
    @PostMapping("/{name}/templates/{key}/publish")
    public ResponseEntity<?> publish(@PathVariable String name, @PathVariable String key,
                                     @RequestParam String reviewRef,
                                     @RequestParam(defaultValue = "ops-panel") String updatedBy) {
        try {
            service.publish(name, key, reviewRef, updatedBy);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 丢弃草稿 */
    @DeleteMapping("/{name}/templates/{key}/draft")
    public ResponseEntity<Void> discardDraft(@PathVariable String name, @PathVariable String key) {
        service.discardDraft(name, key);
        return ResponseEntity.noContent().build();
    }

    /** 版本历史 */
    @GetMapping("/{name}/templates/{key}/history")
    public List<SkillAdminService.VersionView> history(@PathVariable String name,
                                                       @PathVariable String key) {
        return service.history(name, key);
    }

    /** 回滚到历史版本（必填评审单号） */
    @PostMapping("/{name}/templates/{key}/rollback")
    public ResponseEntity<?> rollback(@PathVariable String name, @PathVariable String key,
                                      @RequestParam int versionNo,
                                      @RequestParam String reviewRef,
                                      @RequestParam(defaultValue = "ops-panel") String updatedBy) {
        try {
            service.rollback(name, key, versionNo, reviewRef, updatedBy);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
