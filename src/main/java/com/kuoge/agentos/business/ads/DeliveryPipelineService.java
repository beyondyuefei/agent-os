package com.kuoge.agentos.business.ads;

import com.kuoge.agentos.capability.CapabilityCode;
import com.kuoge.agentos.capability.CapabilityGateway;
import com.kuoge.agentos.capability.CapabilityStore;
import com.kuoge.agentos.capability.GatewayDecision;
import com.kuoge.agentos.capability.PlanCapabilityBinding;
import com.kuoge.agentos.channel.AdChannelAdapter;
import com.kuoge.agentos.channel.AdMapping;
import com.kuoge.agentos.channel.CreationStatus;
import com.kuoge.agentos.channel.Window;
import com.kuoge.agentos.delivery.DeliveryItem;
import com.kuoge.agentos.delivery.DeliveryItemAction;
import com.kuoge.agentos.delivery.DeliveryItemStore;
import com.kuoge.agentos.delivery.DeliveryItemStateMachine;
import com.kuoge.agentos.delivery.DeliveryItemStatus;
import com.kuoge.agentos.skill.SkillContext;
import com.kuoge.agentos.skill.ad.AdCreateExecuteService;
import com.kuoge.agentos.skill.ad.AdFactStore;
import com.kuoge.agentos.skill.ad.AttributionMapService;
import com.kuoge.agentos.skill.ad.DataIngestService;
import com.kuoge.agentos.skill.ad.EvolveSuggestSkill;
import com.kuoge.agentos.skill.ad.ExperienceDistillSkill;
import com.kuoge.agentos.skill.ad.ItemAssembleService;
import com.kuoge.agentos.skill.ad.OpsControlService;
import com.kuoge.agentos.skill.ad.PreflightCheckService;
import com.kuoge.agentos.skill.ad.ReviewDraftSkill;
import com.kuoge.agentos.skill.md.SkillLibrary;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 投流流程编排层（方案 3.7 衔接规则的代码化）：SOP-A 投放链路 + SOP-B 复盘进化链路。
 * 素材 / 母集合就绪事件、人审工作台、cron 触发链统一经本层进入，
 * 各确定性服务 / AI Skill 的判定语义不在本层重复。
 */
public class DeliveryPipelineService {

    /** 网关拒绝（编排层入口统一异常，REST 层映射 409） */
    public static class GatewayRejectedException extends RuntimeException {
        public GatewayRejectedException(String code) {
            super("GATEWAY_REJECTED: " + code);
        }
    }

    /** 建议确认结果：路由 + 执行描述（OPS_CONTROL 已执行 / 其他类型仅留痕） */
    public record SuggestionConfirmResult(String actionRoute, String detail) {}

    private final DeliveryItemStore repository;
    private final DeliveryItemStateMachine stateMachine;
    private final ItemAssembleService itemAssembleService;
    private final InMemoryCollectionReadiness collectionReadiness;
    private final PreflightCheckService preflightCheckService;
    private final AdCreateExecuteService adCreateExecuteService;
    private final OpsControlService opsControlService;
    private final DataIngestService dataIngestService;
    private final AttributionMapService attributionMapService;
    private final ReviewDraftSkill reviewDraftSkill;
    private final ExperienceDistillSkill experienceDistillSkill;
    private final AdFactStore factStore;
    private final AdChannelAdapter channelAdapter;
    private final CapabilityGateway gateway;
    private final CapabilityStore capabilityStore;
    private final SkillLibrary skillLibrary;

    public DeliveryPipelineService(DeliveryItemStore repository,
                                   DeliveryItemStateMachine stateMachine,
                                   ItemAssembleService itemAssembleService,
                                   InMemoryCollectionReadiness collectionReadiness,
                                   PreflightCheckService preflightCheckService,
                                   AdCreateExecuteService adCreateExecuteService,
                                   OpsControlService opsControlService,
                                   DataIngestService dataIngestService,
                                   AttributionMapService attributionMapService,
                                   ReviewDraftSkill reviewDraftSkill,
                                   ExperienceDistillSkill experienceDistillSkill,
                                   AdFactStore factStore,
                                   AdChannelAdapter channelAdapter,
                                   CapabilityGateway gateway,
                                   CapabilityStore capabilityStore,
                                   SkillLibrary skillLibrary) {
        this.repository = repository;
        this.stateMachine = stateMachine;
        this.itemAssembleService = itemAssembleService;
        this.collectionReadiness = collectionReadiness;
        this.preflightCheckService = preflightCheckService;
        this.adCreateExecuteService = adCreateExecuteService;
        this.opsControlService = opsControlService;
        this.dataIngestService = dataIngestService;
        this.attributionMapService = attributionMapService;
        this.reviewDraftSkill = reviewDraftSkill;
        this.experienceDistillSkill = experienceDistillSkill;
        this.factStore = factStore;
        this.channelAdapter = channelAdapter;
        this.gateway = gateway;
        this.capabilityStore = capabilityStore;
        this.skillLibrary = skillLibrary;
    }

    // ---- SOP-A：投放链路 ----

    /** 素材检查通过事件入口（SOP-A Step 2-3）：组装投放项，母集合未就绪则转待补 */
    public DeliveryItem assemble(SkillContext ctx, ItemAssembleService.Input input) {
        return itemAssembleService.execute(ctx, input);
    }

    /** 母集合就绪事件入口（方案 3.7）：标记就绪并对该集合下所有待补项补齐（仍 PENDING 等送审） */
    public List<DeliveryItem> collectionReady(SkillContext ctx, String collectionId) {
        collectionReadiness.markReady(collectionId);
        List<DeliveryItem> completed = new ArrayList<>();
        for (DeliveryItem item : repository.findByTenant(ctx.tenantId())) {
            if (collectionId.equals(item.getCollectionId())
                    && item.getStatus() == DeliveryItemStatus.PENDING
                    && !item.approvedAndLatest()) {
                completed.add(itemAssembleService.complete(ctx, item.getDeliveryItemId()));
                repository.appendAudit(ctx.tenantId() + " COMPLETE " + item.getDeliveryItemId()
                        + " collection ready: " + collectionId);
            }
        }
        return completed;
    }

    /** 送审（SOP-A Step 4）：投前检查集 A 全过 → SUBMIT；不过保持 PENDING 并记未过项 */
    public DeliveryItem submit(SkillContext ctx, String itemId) {
        DeliveryItem item = mustGet(itemId);
        PreflightCheckService.Report report = preflightCheckService.execute(ctx,
                new PreflightCheckService.Input(itemId, PreflightCheckService.CheckSet.A));
        if (!report.pass()) {
            List<String> failed = report.checks().stream()
                    .filter(c -> !c.pass())
                    .map(PreflightCheckService.Check::code)
                    .toList();
            item.setRejectReason("PREFLIGHT_A_FAILED: " + String.join(", ", failed));
            repository.save(item);
            repository.appendAudit(item.getTenantId() + " SUBMIT blocked: " + item.getRejectReason());
            return item;
        }
        stateMachine.transit(item, DeliveryItemAction.SUBMIT, "preflight A passed");
        repository.save(item);
        repository.appendAudit(item.getTenantId() + " SUBMIT -> " + item.getStatus());
        return item;
    }

    /** 负责人人审通过（SOP-A Step 5，一期为 REST 工作台，飞书审批后置） */
    public DeliveryItem approve(SkillContext ctx, String itemId, String operatorId) {
        DeliveryItem item = mustGet(itemId);
        stateMachine.transit(item, DeliveryItemAction.APPROVE, "approved by " + operatorId);
        repository.save(item);
        repository.appendAudit(item.getTenantId() + " APPROVE -> APPROVED operator=" + operatorId);
        return item;
    }

    /** 负责人退回：记原因，仅影响本项 */
    public DeliveryItem reject(SkillContext ctx, String itemId, String reason) {
        DeliveryItem item = mustGet(itemId);
        stateMachine.transit(item, DeliveryItemAction.REJECT, reason);
        repository.save(item);
        repository.appendAudit(item.getTenantId() + " REJECT -> RETURNED reason=" + reason);
        return item;
    }

    /**
     * 补齐重送（升版 / 快照失效）：RESUBMIT 产生新版本且旧快照失效。
     * 一期不自动重组装：重新送审前需先经 assemble/complete 链路按新版本补齐批准快照。
     */
    public DeliveryItem resubmit(SkillContext ctx, String itemId) {
        DeliveryItem item = mustGet(itemId);
        stateMachine.transit(item, DeliveryItemAction.RESUBMIT, "resubmit");
        repository.save(item);
        repository.appendAudit(item.getTenantId() + " RESUBMIT -> READY_FOR_REVIEW version="
                + item.getVersion());
        return item;
    }

    /** 批准后自动创建（SOP-A Step 6）：网关门禁 + preflight B + 幂等在执行服务内完成 */
    public AdCreateExecuteService.Output create(SkillContext ctx, String itemId) {
        return adCreateExecuteService.execute(ctx, itemId);
    }

    /** 启停控制（SOP-A Step 7）：风险动作授权重检 / 停止类独立审计，语义在 OpsControlService */
    public OpsControlService.Output ops(SkillContext ctx, String itemId, OpsControlService.Action action) {
        return opsControlService.execute(ctx, new OpsControlService.Input(itemId, action));
    }

    /** 待查反查（SOP-A Step 8）：已创建 → CONFIRM_CREATED；未创建 → CONFIRM_NOT_CREATED；仍未知保持待查 */
    public DeliveryItem resolvePendingCheck(SkillContext ctx, String itemId) {
        DeliveryItem item = mustGet(itemId);
        if (item.getStatus() != DeliveryItemStatus.PENDING_CHECK) {
            throw new IllegalStateException("RESOLVE_ONLY_FOR_PENDING_CHECK: " + itemId
                    + " status=" + item.getStatus());
        }
        CreationStatus status = channelAdapter.getCreationStatus(item.getPlatformRequestId());
        switch (status) {
            case CREATED -> {
                String platformAdId = channelAdapter.resolvePlatformAdId(item.getPlatformRequestId());
                if (platformAdId != null) {
                    item.setPlatformAdId(platformAdId);
                }
                stateMachine.transit(item, DeliveryItemAction.CONFIRM_CREATED, "recheck created");
                repository.save(item);
                repository.appendAudit(item.getTenantId() + " CONFIRM_CREATED " + itemId
                        + " platformAdId=" + item.getPlatformAdId());
            }
            case NOT_CREATED -> {
                stateMachine.transit(item, DeliveryItemAction.CONFIRM_NOT_CREATED, "recheck not created");
                repository.save(item);
                repository.appendAudit(item.getTenantId() + " CONFIRM_NOT_CREATED " + itemId);
            }
            default -> repository.appendAudit(item.getTenantId() + " RECHECK_STILL_UNKNOWN " + itemId);
        }
        return item;
    }

    // ---- SOP-B：复盘进化链路 ----

    /** 观察快照（方案 8.6 Insight.query）：归因映射输出的结构化状态 + 证据指针 */
    public AttributionMapService.Observation observation(SkillContext ctx, String itemId) {
        return attributionMapService.execute(ctx, itemId);
    }

    /** 数据回流 + 归因映射（SOP-B Step 2-3）：该租户 RUNNING/PAUSED 且已建平台对象的投放项 */
    public int ingestAndMap(SkillContext ctx, String tenantId) {
        int processed = 0;
        Window window = new Window(LocalDate.now().minusDays(1), LocalDate.now());
        for (DeliveryItem item : repository.findByTenant(tenantId)) {
            if ((item.getStatus() == DeliveryItemStatus.RUNNING
                    || item.getStatus() == DeliveryItemStatus.PAUSED)
                    && item.getPlatformAdId() != null) {
                AdMapping mapping = new AdMapping("map_" + item.getDeliveryItemId(),
                        item.getDeliveryItemId(), tenantId, item.getPlatformAdId(), null, null);
                dataIngestService.execute(ctx, new DataIngestService.Input(mapping, window));
                attributionMapService.execute(ctx, item.getDeliveryItemId());
                processed++;
            }
        }
        return processed;
    }

    /** 复盘草稿（SOP-B Step 4）：Observation 注入 ReviewDraftSkill，AI 只出草稿不写状态 */
    public String reviewDraft(SkillContext ctx, String itemId) {
        AttributionMapService.Observation obs = observation(ctx, itemId);
        return reviewDraftSkill.run(ctx, obs).output();
    }

    /**
     * 效果进化建议（SOP-B Step 6）：先过网关（ad.effect.evolve / EVOLVE_SUGGEST），
     * ALLOW / DEGRADED 时注入 policy 并按 plan_capability_binding.skill_version
     * 决定版本线（1.x = PRO / 2.x = KA）构造对应 EvolveSuggestSkill。
     */
    public EvolveSuggestSkill.Suggestion evolveSuggest(SkillContext ctx, String itemId) {
        GatewayDecision decision = gateway.check(ctx.tenantId(), CapabilityCode.AD_EFFECT_EVOLVE,
                "EVOLVE_SUGGEST", itemId, List.of());
        if (decision.rejected()) {
            throw new GatewayRejectedException(
                    decision.rejectCode() == null ? "UNKNOWN" : decision.rejectCode().name());
        }
        SkillContext ctxWithPolicy = ctx.withPolicy(decision.policy());
        String versionLine = decision.skillVersion() == null ? "1.x" : decision.skillVersion();
        EvolveSuggestSkill skill = new EvolveSuggestSkill(versionLine, skillLibrary);
        return skill.run(ctxWithPolicy, observation(ctx, itemId)).output();
    }

    /**
     * 建议确认（SOP-B Step 6 后半，铁律：未确认不执行）：按服务端重算的建议 actionRoute 路由。
     * OPS_CONTROL → OpsControlService 执行对应启停动作；
     * CONTENT / BUDGET / NONE 一期只写审计留痕不执行（KA 自动执行为合同预留）。
     */
    public SuggestionConfirmResult confirmSuggestion(SkillContext ctx, String itemId,
                                                     OpsControlService.Action action) {
        EvolveSuggestSkill.Suggestion suggestion = evolveSuggest(ctx, itemId);
        String route = suggestion.actionRoute() == null ? "NONE" : suggestion.actionRoute();
        if ("OPS_CONTROL".equals(route)) {
            OpsControlService.Output output = ops(ctx, itemId, action);
            repository.appendAudit(ctx.tenantId() + " SUGGESTION_CONFIRMED " + itemId
                    + " route=OPS_CONTROL action=" + action + " -> " + output.newStatus());
            return new SuggestionConfirmResult(route, "executed: " + action + " -> " + output.newStatus());
        }
        repository.appendAudit(ctx.tenantId() + " SUGGESTION_CONFIRMED " + itemId
                + " route=" + route + " action=" + action + " (audit only, not executed)");
        return new SuggestionConfirmResult(route, "audit only, not executed");
    }

    /** 复盘确认 → 经验沉淀（SOP-B Step 7）：kaLine 由该租户绑定的 skillVersion 是否 2.x 决定 */
    public AdFactStore.Experience confirmReviewAndDistill(SkillContext ctx, String itemId,
                                                          String confirmedReview) {
        ExperienceDistillSkill skill = new ExperienceDistillSkill(kaLine(ctx.tenantId()),
                factStore, skillLibrary);
        AdFactStore.Experience candidate = skill.run(ctx, confirmedReview).output();
        repository.appendAudit(ctx.tenantId() + " EXPERIENCE_DISTILLED " + itemId
                + " expId=" + candidate.id() + " kaLine=" + kaLine(ctx.tenantId()));
        return candidate;
    }

    /** 经验审核生效（验收规则 9：未审核规则不可被任何 Skill 读为生效配置） */
    public AdFactStore.Experience approveExperience(SkillContext ctx, String expId) {
        AdFactStore.Experience candidate = factStore.allExperiences().stream()
                .filter(e -> e.id().equals(expId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("EXPERIENCE_NOT_FOUND: " + expId));
        AdFactStore.Experience approved = experienceDistillSkill.approve(candidate);
        repository.appendAudit(ctx.tenantId() + " EXPERIENCE_APPROVED " + expId);
        return approved;
    }

    /** 该租户 ad.effect.evolve 绑定是否为 KA 版本线（2.x） */
    private boolean kaLine(String tenantId) {
        return capabilityStore.findTenantPlan(tenantId)
                .flatMap(tp -> capabilityStore.findBinding(tp.planCode(),
                        CapabilityCode.AD_EFFECT_EVOLVE.code()))
                .map(PlanCapabilityBinding::skillVersion)
                .map(v -> v.startsWith("2"))
                .orElse(false);
    }

    private DeliveryItem mustGet(String itemId) {
        return repository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("ITEM_NOT_FOUND: " + itemId));
    }
}
