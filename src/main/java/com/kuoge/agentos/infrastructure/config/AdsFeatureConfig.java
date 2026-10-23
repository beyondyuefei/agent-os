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
import com.kuoge.agentos.infrastructure.persistence.JpaSkillOverrideStore;
import com.kuoge.agentos.infrastructure.persistence.repository.SkillTemplateRepository;
import com.kuoge.agentos.skill.md.SkillLibrary;
import com.kuoge.agentos.skill.md.SkillOverrideStore;
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

    /** SKILL.md 提示词库（三级渐进加载；数据库覆盖优先于 classpath 默认模板） */
    @Bean
    public SkillLibrary skillLibrary(SkillOverrideStore skillOverrideStore) {
        return new SkillLibrary(skillOverrideStore);
    }

    @Bean
    public SkillOverrideStore skillOverrideStore(SkillTemplateRepository repository) {
        return new JpaSkillOverrideStore(repository);
    }

    @Bean
    public SkillAdminService skillAdminService(SkillLibrary skillLibrary,
                                               SkillOverrideStore skillOverrideStore) {
        return new SkillAdminService(skillLibrary, skillOverrideStore);
    }

    /** 启动种子：jar 内 md 模板初始化入库（已有运营覆盖的 skill 不冲掉） */
    @Bean
    public SkillTemplateBootstrap skillTemplateBootstrap(SkillTemplateRepository repository) {
        return new SkillTemplateBootstrap(repository);
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
