package com.kuoge.agentos.infrastructure.config;

import com.kuoge.agentos.capability.CapabilityGateway;
import com.kuoge.agentos.capability.CapabilityStore;
import com.kuoge.agentos.capability.DeliveryItemApprovalProvider;
import com.kuoge.agentos.capability.RevocationNotifier;
import com.kuoge.agentos.channel.AdChannelAdapter;
import com.kuoge.agentos.channel.meta.MetaAdChannelAdapter;
import com.kuoge.agentos.channel.meta.MetaProperties;
import com.kuoge.agentos.channel.mock.MockAdChannelAdapter;
import com.kuoge.agentos.delivery.BudgetLedger;
import com.kuoge.agentos.delivery.DeliveryItemRepository;
import com.kuoge.agentos.delivery.DeliveryItemStateMachine;
import com.kuoge.agentos.api.SkillAdminService;
import com.kuoge.agentos.infrastructure.persistence.JpaSkillTemplateStore;
import com.kuoge.agentos.infrastructure.persistence.repository.SkillTemplateRepository;
import com.kuoge.agentos.infrastructure.persistence.repository.SkillTemplateVersionRepository;
import com.kuoge.agentos.skill.md.SkillLibrary;
import com.kuoge.agentos.skill.md.SkillTemplateStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 广告投流业务装配（feature/ads）。
 * 通道切换只改配置不改代码：agentos.channel.meta.mode = mock | live。
 */
@Configuration
public class AdsFeatureConfig {

    @Bean
    public CapabilityStore capabilityStore() {
        return new CapabilityStore();
    }

    @Bean
    public DeliveryItemRepository deliveryItemRepository() {
        return new DeliveryItemRepository();
    }

    @Bean
    public DeliveryItemStateMachine deliveryItemStateMachine() {
        return new DeliveryItemStateMachine();
    }

    @Bean
    public BudgetLedger budgetLedger() {
        return new BudgetLedger();
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
    public DeliveryItemApprovalProvider deliveryItemApprovalProvider(DeliveryItemRepository repository) {
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
}
