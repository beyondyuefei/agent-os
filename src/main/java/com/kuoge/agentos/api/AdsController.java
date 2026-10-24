package com.kuoge.agentos.api;

import com.kuoge.agentos.business.ads.DeliveryPipelineService;
import com.kuoge.agentos.delivery.DeliveryItem;
import com.kuoge.agentos.delivery.DeliveryItemStore;
import com.kuoge.agentos.delivery.StateTransitionException;
import com.kuoge.agentos.infrastructure.tenant.TenantContext;
import com.kuoge.agentos.skill.SkillContext;
import com.kuoge.agentos.skill.ad.AdCreateExecuteService;
import com.kuoge.agentos.skill.ad.AdFactStore;
import com.kuoge.agentos.skill.ad.AttributionMapService;
import com.kuoge.agentos.skill.ad.EvolveSuggestSkill;
import com.kuoge.agentos.skill.ad.ItemAssembleService;
import com.kuoge.agentos.skill.ad.OpsControlService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 广告投流工作台 REST 入口（方案 SOP-A/B，一期为人工工作台形态，飞书审批后置）。
 * 租户优先取 X-Tenant-Id 头绑定的 TenantContext，缺省回退请求参数 tenantId。
 */
@RestController
@RequestMapping("/api/ads")
public class AdsController {

    public record CreateItemRequest(String imageId, String productId, String planId,
                                    String collectionId, long budgetAmount) {}
    public record ApproveRequest(String operatorId) {}
    public record RejectRequest(String reason) {}
    public record OpsRequest(String action) {}
    public record ConfirmSuggestionRequest(String action) {}
    public record ConfirmReviewRequest(String confirmedReview) {}

    private final DeliveryPipelineService pipeline;
    private final DeliveryItemStore repository;

    public AdsController(DeliveryPipelineService pipeline, DeliveryItemStore repository) {
        this.pipeline = pipeline;
        this.repository = repository;
    }

    /** 建项（素材检查通过事件入口；母集合未就绪则转待补） */
    @PostMapping("/items")
    public DeliveryItem createItem(@RequestParam(required = false) String tenantId,
                                   @RequestBody CreateItemRequest request) {
        return pipeline.assemble(ctx(tenantId),
                new ItemAssembleService.Input(request.imageId(), request.productId(),
                        request.planId(), request.collectionId(), request.budgetAmount()));
    }

    /** 母集合就绪：标记后对该集合下所有待补项补齐 */
    @PostMapping("/collections/{collectionId}/ready")
    public List<DeliveryItem> collectionReady(@RequestParam(required = false) String tenantId,
                                              @PathVariable String collectionId) {
        return pipeline.collectionReady(ctx(tenantId), collectionId);
    }

    @PostMapping("/items/{id}/submit")
    public DeliveryItem submit(@RequestParam(required = false) String tenantId,
                               @PathVariable String id) {
        return pipeline.submit(ctx(tenantId), id);
    }

    @PostMapping("/items/{id}/approve")
    public DeliveryItem approve(@RequestParam(required = false) String tenantId,
                                @PathVariable String id,
                                @RequestBody(required = false) ApproveRequest request) {
        String operatorId = request == null || request.operatorId() == null
                ? "ops-panel" : request.operatorId();
        return pipeline.approve(ctx(tenantId), id, operatorId);
    }

    @PostMapping("/items/{id}/reject")
    public DeliveryItem reject(@RequestParam(required = false) String tenantId,
                               @PathVariable String id,
                               @RequestBody RejectRequest request) {
        return pipeline.reject(ctx(tenantId), id, request.reason());
    }

    @PostMapping("/items/{id}/resubmit")
    public DeliveryItem resubmit(@RequestParam(required = false) String tenantId,
                                 @PathVariable String id) {
        return pipeline.resubmit(ctx(tenantId), id);
    }

    /** 批准后自动创建（SOP-A Step 6） */
    @PostMapping("/items/{id}/create")
    public AdCreateExecuteService.Output create(@RequestParam(required = false) String tenantId,
                                                @PathVariable String id) {
        return pipeline.create(ctx(tenantId), id);
    }

    /** 启停控制：action = ENABLE | PAUSE | RESUME | END */
    @PostMapping("/items/{id}/ops")
    public OpsControlService.Output ops(@RequestParam(required = false) String tenantId,
                                        @PathVariable String id,
                                        @RequestBody OpsRequest request) {
        return pipeline.ops(ctx(tenantId), id, OpsControlService.Action.valueOf(request.action()));
    }

    /** 待查反查（SOP-A Step 8） */
    @PostMapping("/items/{id}/resolve-check")
    public DeliveryItem resolveCheck(@RequestParam(required = false) String tenantId,
                                     @PathVariable String id) {
        return pipeline.resolvePendingCheck(ctx(tenantId), id);
    }

    /** 按租户列出投放项 */
    @GetMapping("/items")
    public List<DeliveryItem> listItems(@RequestParam(required = false) String tenantId) {
        return repository.findByTenant(ctx(tenantId).tenantId());
    }

    @GetMapping("/items/{id}")
    public DeliveryItem item(@PathVariable String id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ITEM_NOT_FOUND: " + id));
    }

    /** 观察快照（方案 8.6 Insight.query） */
    @GetMapping("/items/{id}/observation")
    public AttributionMapService.Observation observation(@RequestParam(required = false) String tenantId,
                                                         @PathVariable String id) {
        return pipeline.observation(ctx(tenantId), id);
    }

    /** 该项相关的审计留痕 */
    @GetMapping("/items/{id}/audit")
    public List<String> audit(@PathVariable String id) {
        return repository.auditTrail().stream()
                .filter(entry -> entry.contains(id))
                .toList();
    }

    /** 复盘草稿（SOP-B Step 4） */
    @PostMapping("/items/{id}/review-draft")
    public Map<String, String> reviewDraft(@RequestParam(required = false) String tenantId,
                                           @PathVariable String id) {
        return Map.of("draft", pipeline.reviewDraft(ctx(tenantId), id));
    }

    /** 效果进化建议（SOP-B Step 6，版本线由绑定 skill_version 决定） */
    @PostMapping("/items/{id}/evolve-suggest")
    public EvolveSuggestSkill.Suggestion evolveSuggest(@RequestParam(required = false) String tenantId,
                                                       @PathVariable String id) {
        return pipeline.evolveSuggest(ctx(tenantId), id);
    }

    /** 建议确认：按 actionRoute 路由执行或留痕（铁律：未确认不执行） */
    @PostMapping("/items/{id}/confirm-suggestion")
    public DeliveryPipelineService.SuggestionConfirmResult confirmSuggestion(
            @RequestParam(required = false) String tenantId,
            @PathVariable String id,
            @RequestBody ConfirmSuggestionRequest request) {
        return pipeline.confirmSuggestion(ctx(tenantId), id,
                OpsControlService.Action.valueOf(request.action()));
    }

    /** 复盘确认 → 经验沉淀（SOP-B Step 7） */
    @PostMapping("/items/{id}/confirm-review")
    public AdFactStore.Experience confirmReview(@RequestParam(required = false) String tenantId,
                                                @PathVariable String id,
                                                @RequestBody ConfirmReviewRequest request) {
        return pipeline.confirmReviewAndDistill(ctx(tenantId), id, request.confirmedReview());
    }

    /** 经验审核生效 */
    @PostMapping("/experiences/{expId}/approve")
    public AdFactStore.Experience approveExperience(@RequestParam(required = false) String tenantId,
                                                    @PathVariable String expId) {
        return pipeline.approveExperience(ctx(tenantId), expId);
    }

    /** 租户：优先 TenantContext（X-Tenant-Id 头），取不到再用请求参数 */
    private SkillContext ctx(String tenantId) {
        String t = TenantContext.get();
        if ((t == null || "default".equals(t)) && tenantId != null && !tenantId.isBlank()) {
            t = tenantId;
        }
        return SkillContext.of(t == null ? "default" : t);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
    }

    @ExceptionHandler({IllegalStateException.class, StateTransitionException.class})
    public ResponseEntity<Map<String, String>> conflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", String.valueOf(e.getMessage())));
    }

    /** 网关拒绝 / 投前检查不过 / 额度不足 / 通道不可用等业务阻断 → 409 */
    @ExceptionHandler({
            DeliveryPipelineService.GatewayRejectedException.class,
            AdCreateExecuteService.GatewayRejectedException.class,
            OpsControlService.AuthRecheckFailedException.class,
            OpsControlService.ChannelUnavailableException.class,
            com.kuoge.agentos.skill.ad.PreflightBFailedException.class,
            ItemAssembleService.QuotaInsufficientException.class})
    public ResponseEntity<Map<String, String>> businessBlocked(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", String.valueOf(e.getMessage())));
    }
}
