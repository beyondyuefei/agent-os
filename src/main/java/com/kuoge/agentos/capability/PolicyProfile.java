package com.kuoge.agentos.capability;

import java.util.List;
import java.util.Map;

/**
 * 策略档位：六轴全部落在这一个值对象上（方案 2.1 policy_profile / 2.3）。
 * 同一能力不同套餐档位的差异 = 本对象的不同版本，不加 if 分支。
 */
public record PolicyProfile(
        String code,
        int version,
        AutonomyLevel autonomyLevel,
        /** 运行频率轴：cron 表达式 */
        String cronExpr,
        /** 分析深度轴：阈值配置 */
        Map<String, Object> thresholds,
        /** 资源模型轴：模型档位与降级链 */
        Map<String, Object> modelRoute,
        /** 数据范围轴：可读数据清单 */
        List<String> dataScope
) {

    public PolicyProfile {
        thresholds = thresholds == null ? Map.of() : Map.copyOf(thresholds);
        modelRoute = modelRoute == null ? Map.of() : Map.copyOf(modelRoute);
        dataScope = dataScope == null ? List.of() : List.copyOf(dataScope);
    }

    /** 数据越权检查：请求的数据集是否全部在策略允许范围内 */
    public boolean coversDataScope(List<String> requested) {
        return dataScope.containsAll(requested);
    }
}
