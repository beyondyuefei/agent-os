package com.kuoge.agentos.capability;

/**
 * 能力编码（方案 8.1）。写路径调用网关时必须声明目标能力。
 */
public enum CapabilityCode {

    AD_DELIVERY_OPS("ad.delivery.ops"),
    AD_DATA_OBSERVE("ad.data.observe"),
    AD_CREATIVE_GENERATE("ad.creative.generate"),
    AD_EFFECT_EVOLVE("ad.effect.evolve");

    private final String code;

    CapabilityCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static CapabilityCode of(String code) {
        for (CapabilityCode c : values()) {
            if (c.code.equals(code)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown capability code: " + code);
    }
}
