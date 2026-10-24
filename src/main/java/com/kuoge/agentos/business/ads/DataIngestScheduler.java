package com.kuoge.agentos.business.ads;

import com.kuoge.agentos.capability.CapabilityCode;
import com.kuoge.agentos.capability.CapabilityStore;
import com.kuoge.agentos.capability.PlanCapabilityBinding;
import com.kuoge.agentos.capability.PolicyProfile;
import com.kuoge.agentos.capability.TenantPlan;
import com.kuoge.agentos.skill.SkillContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据回流 cron 触发链（方案「cron 读 policy」：同一 Skill 代码，频率由策略配置驱动，
 * PRO 每日 / KA 每小时）。
 * 每 tick 遍历全部租户套餐 → binding(ad.data.observe) → PolicyProfile.cronExpr，
 * 到期则对该租户执行 ingestAndMap（SOP-B Step 2-3）；单租户失败不影响其他租户。
 */
public class DataIngestScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataIngestScheduler.class);

    private final CapabilityStore capabilityStore;
    private final DeliveryPipelineService pipeline;
    /** 每租户上次触发时间（内存即可，重启丢失只影响首 tick 基准） */
    private final Map<String, LocalDateTime> lastRun = new ConcurrentHashMap<>();

    public DataIngestScheduler(CapabilityStore capabilityStore, DeliveryPipelineService pipeline) {
        this.capabilityStore = capabilityStore;
        this.pipeline = pipeline;
    }

    @Scheduled(fixedDelayString = "${agentos.ads.ingest-tick-ms:60000}",
            initialDelayString = "${agentos.ads.ingest-initial-delay-ms:10000}")
    public void tick() {
        tick(LocalDateTime.now());
    }

    /** 单 tick 扫描（抽成可手动调用便于测试） */
    void tick(LocalDateTime now) {
        for (TenantPlan tenantPlan : capabilityStore.listTenantPlans()) {
            try {
                tickTenant(tenantPlan, now);
            } catch (RuntimeException e) {
                // 单租户失败不影响其他租户
                log.warn("ads ingest tick failed for tenant {}: {}", tenantPlan.tenantId(), e.getMessage());
            }
        }
    }

    private void tickTenant(TenantPlan tenantPlan, LocalDateTime now) {
        PlanCapabilityBinding binding = capabilityStore.findBinding(tenantPlan.planCode(),
                CapabilityCode.AD_DATA_OBSERVE.code()).orElse(null);
        if (binding == null || !binding.enabled()) {
            return;
        }
        PolicyProfile policy = capabilityStore
                .findPolicy(binding.policyProfileCode(), binding.policyProfileVersion())
                .orElse(null);
        String cronExpr = policy == null ? null : policy.cronExpr();
        if (cronExpr == null || cronExpr.isBlank()) {
            return;
        }
        CronExpression cron = CronExpression.parse(normalize(cronExpr));
        // 首次见到该租户：以当前时间为基准登记，不补跑历史
        LocalDateTime last = lastRun.putIfAbsent(tenantPlan.tenantId(), now);
        if (last == null) {
            return;
        }
        LocalDateTime next = cron.next(last);
        if (next != null && !next.isAfter(now)) {
            lastRun.put(tenantPlan.tenantId(), now);
            int processed = pipeline.ingestAndMap(SkillContext.of(tenantPlan.tenantId()),
                    tenantPlan.tenantId());
            log.info("ads ingest triggered for tenant {} (cron [{}]), processed {} items",
                    tenantPlan.tenantId(), cronExpr, processed);
        }
    }

    /** 兼容 5 段 cron（分 时 日 月 周）→ Spring CronExpression 需要的 6 段（补秒位） */
    private static String normalize(String cronExpr) {
        return cronExpr.trim().split("\\s+").length == 5 ? "0 " + cronExpr.trim() : cronExpr.trim();
    }
}
