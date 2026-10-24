package com.kuoge.agentos.infrastructure.persistence.ads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * ads 持久层 JSON 支撑：record / 复杂值对象统一序列化为 JSON 字符串列。
 * 注册 JavaTimeModule 以支持 LocalDate / LocalDateTime 字段（InsightReport.window 等）。
 */
final class AdsJsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private AdsJsonSupport() {}

    static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 序列化失败: " + value.getClass().getSimpleName(), e);
        }
    }

    static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 反序列化失败: " + type.getSimpleName(), e);
        }
    }

    static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 反序列化失败: " + type, e);
        }
    }
}
