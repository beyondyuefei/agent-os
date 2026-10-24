package com.kuoge.agentos.infrastructure.config;

import com.kuoge.agentos.business.ads.DataIngestScheduler;
import com.kuoge.agentos.business.ads.DeliveryPipelineService;
import com.kuoge.agentos.business.ads.InMemoryCollectionReadiness;
import com.kuoge.agentos.capability.CapabilityGateway;
import com.kuoge.agentos.capability.CapabilityStore;
import com.kuoge.agentos.capability.DeliveryItemApprovalProvider;
import com.kuoge.agentos.capability.RevocationNotifier;
import com.kuoge.agentos.channel.AdChannelAdapter;
import com.kuoge.agentos.channel.meta.MetaAdChannelAdapter;
import com.kuoge.agentos.channel.meta.MetaProperties;
import com.kuoge.agentos.channel.mock.MockAdChannelAdapter;
import com.kuoge.agentos.core.client.LlmClient;
import com.kuoge.agentos.delivery.BudgetLedger;
import com.kuoge.agentos.delivery.DeliveryItemStore;
import com.kuoge.agentos.delivery.DeliveryItemStateMachine;
import com.kuoge.agentos.api.SkillAdminService;
import com.kuoge.agentos.infrastructure.persistence.JpaSkillTemplateStore;
import com.kuoge.agentos.infrastructure.persistence.ads.JpaAdFactStore;
import com.kuoge.agentos.infrastructure.persistence.ads.JpaBudgetLedger;
import com.kuoge.agentos.infrastructure.persistence.ads.JpaCapabilityStore;
import com.kuoge.agentos.infrastructure.persistence.ads.JpaDeliveryItemStore;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.AdFactPlatformRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.AttributionRecordRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.AuditEntryRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.BudgetAccountRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.BudgetAuthorizationRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.CapabilityDefRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.CapabilityInvokeLogRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.DeliveryItemJpaRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.ExperienceRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.GapTagRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.PlanCapabilityBindingRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.PlanDefRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.PolicyProfileRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.QuotaProfileRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.TenantPlanRepository;
import com.kuoge.agentos.infrastructure.persistence.ads.repository.UnknownOrderRepository;
import com.kuoge.agentos.infrastructure.persistence.repository.SkillTemplateRepository;
import com.kuoge.agentos.infrastructure.persistence.repository.SkillTemplateVersionRepository;
import com.kuoge.agentos.skill.ad.AdCreateExecuteService;
import com.kuoge.agentos.skill.ad.AdFactStore;
import com.kuoge.agentos.skill.ad.AttributionMapService;
import com.kuoge.agentos.skill.ad.DataIngestService;
import com.kuoge.agentos.skill.ad.EvidenceGuard;
import com.kuoge.agentos.skill.ad.ExperienceDistillSkill;
import com.kuoge.agentos.skill.ad.ItemAssembleService;
import com.kuoge.agentos.skill.ad.OpsControlService;
import com.kuoge.agentos.skill.ad.PreflightCheckService;
import com.kuoge.agentos.skill.ad.ReviewDraftSkill;
import com.kuoge.agentos.skill.md.SkillLibrary;
import com.kuoge.agentos.skill.md.SkillTemplateStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 广告投流业务装配（feature/ads）。
 * 通道切换只改配置不改代码：agentos.channel.meta.mode = mock | live。
 */
@Configuration
@EnableScheduling
public class AdsFeatureConfig {

    /** 能力域存储（方案 2.1 八表，JPA 持久化；单测 new InMemoryCapabilityStore） */
    @Bean
    public CapabilityStore capabilityStore(CapabilityDefRepository capabilityRepository,
                                           PlanDefRepository planRepository,
                                           TenantPlanRepository tenantPlanRepository,
                                           PlanCapabilityBindingRepository bindingRepository,
                                           PolicyProfileRepository policyRepository,
                                           QuotaProfileRepository quotaRepository,
                                           CapabilityInvokeLogRepository invokeLogRepository,
                                           BudgetAuthorizationRepository budgetAuthorizationRepository) {
        return new JpaCapabilityStore(capabilityRepository, planRepository, tenantPlanRepository,
                bindingRepository, policyRepository, quotaRepository,
                invokeLogRepository, budgetAuthorizationRepository);
    }

    /** 投放项存储（JPA 持久化；单测 new InMemoryDeliveryItemStore） */
    @Bean
    public DeliveryItemStore deliveryItemStore(DeliveryItemJpaRepository itemRepository,
                                               AuditEntryRepository auditRepository) {
        return new JpaDeliveryItemStore(itemRepository, auditRepository);
    }

    @Bean
    public DeliveryItemStateMachine deliveryItemStateMachine() {
        return new DeliveryItemStateMachine();
    }

    /** 预算台账（JPA + 悲观锁保恒等式；单测 new InMemoryBudgetLedger） */
    @Bean
    public BudgetLedger budgetLedger(BudgetAccountRepository budgetAccountRepository) {
        return new JpaBudgetLedger(budgetAccountRepository);
    }

    /** Skill 契约库（contract.yaml 常驻；生效内容读数据库，缺省回退契约默认值） */
    @Bean
    public SkillLibrary skillLibrary(SkillTemplateStore skillTemplateStore) {
        return new SkillLibrary(skillTemplateStore);
    }

    @Bean
    public SkillTemplateStore skillTemplateStore(SkillTemplateRepository repository) {
        return new JpaSkillTemplateStore(repository);
    }

    @Bean
    public SkillAdminService skillAdminService(SkillLibrary skillLibrary,
                                               SkillTemplateStore skillTemplateStore,
                                               SkillTemplateVersionRepository versionRepository) {
        return new SkillAdminService(skillLibrary, skillTemplateStore, versionRepository);
    }

    /** 启动种子：contract.yaml 默认内容初始化入库（已有运营发布的 skill 不冲掉） */
    @Bean
    public SkillTemplateBootstrap skillTemplateBootstrap(
            SkillTemplateRepository templateRepository,
            SkillTemplateVersionRepository versionRepository) {
        return new SkillTemplateBootstrap(templateRepository, versionRepository);
    }

    @Bean
    public DeliveryItemApprovalProvider deliveryItemApprovalProvider(DeliveryItemStore repository) {
        return (tenantId, deliveryItemId) -> repository.findById(deliveryItemId)
                .filter(item -> item.getTenantId().equals(tenantId))
                .map(item -> item.approvedAndLatest())
                .orElse(false);
    }

    @Bean
    public RevocationNotifier revocationNotifier() {
        // mock：真实实现接飞书通知通道
        return (tenantId, reason) -> { };
    }

    @Bean
    public CapabilityGateway capabilityGateway(CapabilityStore store,
                                               DeliveryItemApprovalProvider approvalProvider,
                                               RevocationNotifier notifier) {
        return new CapabilityGateway(store, approvalProvider, notifier);
    }

    /** 默认 Mock 通道（一期交付） */
    @Bean
    @ConditionalOnProperty(name = "agentos.channel.meta.mode", havingValue = "mock", matchIfMissing = true)
    public AdChannelAdapter mockAdChannelAdapter() {
        return new MockAdChannelAdapter();
    }

    /** 真实 Meta 通道（配置 mode=live 时装配） */
    @Bean
    @ConditionalOnProperty(name = "agentos.channel.meta.mode", havingValue = "live")
    public AdChannelAdapter metaAdChannelAdapter(MetaProperties properties) {
        return new MetaAdChannelAdapter(properties);
    }

    // ---- 投流 Skill / 编排装配（SOP-A/B 触发链） ----

    /** 广告事实与经验存储（JPA 持久化；单测 new InMemoryAdFactStore） */
    @Bean
    public AdFactStore adFactStore(AdFactPlatformRepository platformRepository,
                                   AttributionRecordRepository attributionRepository,
                                   UnknownOrderRepository unknownOrderRepository,
                                   ExperienceRepository experienceRepository,
                                   GapTagRepository gapTagRepository) {
        return new JpaAdFactStore(platformRepository, attributionRepository,
                unknownOrderRepository, experienceRepository, gapTagRepository);
    }

    /** 母集合就绪状态（内存实现：未知集合 = 未就绪，由 ready 端点标记） */
    @Bean
    public InMemoryCollectionReadiness collectionReadiness() {
        return new InMemoryCollectionReadiness();
    }

    @Bean
    public ItemAssembleService itemAssembleService(DeliveryItemStore repository,
                                                   BudgetLedger budgetLedger,
                                                   InMemoryCollectionReadiness collectionReadiness) {
        return new ItemAssembleService(repository, budgetLedger, collectionReadiness);
    }

    @Bean
    public PreflightCheckService preflightCheckService(DeliveryItemStore repository,
                                                       CapabilityStore capabilityStore) {
        return new PreflightCheckService(repository, capabilityStore);
    }

    @Bean
    public AdCreateExecuteService adCreateExecuteService(CapabilityGateway gateway,
                                                         AdChannelAdapter channelAdapter,
                                                         DeliveryItemStore repository,
                                                         DeliveryItemStateMachine stateMachine,
                                                         PreflightCheckService preflightCheckService) {
        return new AdCreateExecuteService(gateway, channelAdapter, repository, stateMachine,
                preflightCheckService);
    }

    @Bean
    public OpsControlService opsControlService(CapabilityGateway gateway,
                                               DeliveryItemStore repository,
                                               DeliveryItemStateMachine stateMachine,
                                               AdChannelAdapter channelAdapter,
                                               PreflightCheckService preflightCheckService) {
        return new OpsControlService(gateway, repository, stateMachine, channelAdapter,
                preflightCheckService);
    }

    @Bean
    public DataIngestService dataIngestService(AdChannelAdapter channelAdapter,
                                               AdFactStore factStore) {
        return new DataIngestService(channelAdapter, factStore);
    }

    @Bean
    public AttributionMapService attributionMapService(DeliveryItemStore repository,
                                                       AdFactStore factStore) {
        return new AttributionMapService(repository, factStore);
    }

    /** PostReasoning 证据指针校验（方案「幻觉校验」）：复盘草稿 / 进化建议共用的确定性规则 */
    @Bean
    public EvidenceGuard evidenceGuard() {
        return new EvidenceGuard();
    }

    @Bean
    public ReviewDraftSkill reviewDraftSkill(LlmClient llmClient, SkillLibrary skillLibrary,
                                             EvidenceGuard evidenceGuard) {
        return new ReviewDraftSkill(llmClient, skillLibrary, evidenceGuard);
    }

    /**
     * 经验沉淀默认 PRO 实例（kaLine=false，仅作 Bean 占位）；
     * 实际按租户绑定的版本线在 DeliveryPipelineService 内构造。
     */
    @Bean
    public ExperienceDistillSkill experienceDistillSkill(AdFactStore factStore,
                                                         SkillLibrary skillLibrary) {
        return new ExperienceDistillSkill(false, factStore, skillLibrary);
    }

    /** 投流流程编排层（方案 3.7 衔接规则）：SOP-A/B 的统一入口 */
    @Bean
    public DeliveryPipelineService deliveryPipelineService(DeliveryItemStore repository,
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
        return new DeliveryPipelineService(repository, stateMachine, itemAssembleService,
                collectionReadiness, preflightCheckService, adCreateExecuteService, opsControlService,
                dataIngestService, attributionMapService, reviewDraftSkill, experienceDistillSkill,
                factStore, channelAdapter, gateway, capabilityStore, skillLibrary);
    }

    /** 数据回流 cron 触发链：cron 读 policy（PRO 每日 / KA 每小时） */
    @Bean
    public DataIngestScheduler dataIngestScheduler(CapabilityStore capabilityStore,
                                                   DeliveryPipelineService pipeline) {
        return new DataIngestScheduler(capabilityStore, pipeline);
    }
}
