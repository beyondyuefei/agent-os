package com.kuoge.agentos.business.service.crossborder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.Map;

/**
 * CrossBorder 管理后台 API 客户端。
 *
 * <p>统一处理登录、Token 刷新、GET/POST 请求和统一响应包装。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossBorderApiClient {

    private final CrossBorderProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.builder().build();

    private volatile String accessToken;
    private volatile long tokenExpireAt;

    /**
     * GET 请求，返回统一响应中的 data 节点。
     */
    public JsonNode get(String path, Map<String, String> params) {
        return executeWithAuth(false, path, params, null);
    }

    /**
     * POST 请求，返回统一响应中的 data 节点。
     */
    public JsonNode post(String path, Object body) {
        return executeWithAuth(true, path, Map.of(), body);
    }

    private JsonNode executeWithAuth(boolean isPost, String path,
                                     Map<String, String> params, Object body) {
        ensureToken();
        try {
            return doRequest(isPost, path, params, body);
        } catch (CrossBorderApiException e) {
            // Token 失效时重试一次
            if (e.getStatus() != null && e.getStatus().isSameCodeAs(org.springframework.http.HttpStatus.UNAUTHORIZED)) {
                log.warn("CrossBorder token may expired, re-login and retry once");
                accessToken = null;
                ensureToken();
                return doRequest(isPost, path, params, body);
            }
            throw e;
        }
    }

    private JsonNode doRequest(boolean isPost, String path,
                               Map<String, String> params, Object body) {
        String url = properties.getBaseUrl() + path;
        String responseBody;
        if (isPost) {
            responseBody = restClient.post()
                    .uri(url, builder -> addParams(builder, params))
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new CrossBorderApiException("CrossBorder POST " + path + " failed: " + res.getStatusCode(),
                                res.getStatusCode());
                    })
                    .body(String.class);
        } else {
            responseBody = restClient.get()
                    .uri(url, builder -> addParams(builder, params))
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new CrossBorderApiException("CrossBorder GET " + path + " failed: " + res.getStatusCode(),
                                res.getStatusCode());
                    })
                    .body(String.class);
        }
        return unwrap(responseBody, path);
    }

    private URI addParams(UriBuilder builder, Map<String, String> params) {
        MultiValueMap<String, String> multi = new LinkedMultiValueMap<>();
        params.forEach((k, v) -> {
            if (k != null && v != null) {
                multi.add(k, v);
            }
        });
        return builder.queryParams(multi).build();
    }

    private JsonNode unwrap(String responseBody, String path) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode success = root.path("success");
            if (!success.isMissingNode() && !success.asBoolean(true)) {
                String error = root.path("error").asText(root.path("codeDesc").asText("请求失败"));
                throw new CrossBorderApiException("CrossBorder " + path + " error: " + error, (HttpStatusCode) null);
            }
            return root.path("data");
        } catch (CrossBorderApiException e) {
            throw e;
        } catch (Exception e) {
            throw new CrossBorderApiException("Failed to parse CrossBorder response for " + path, e);
        }
    }

    private synchronized void ensureToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireAt - 60_000) {
            return;
        }
        if (!properties.isEnabled()) {
            throw new IllegalStateException("CrossBorder client is disabled");
        }
        if (properties.getEmail() == null || properties.getPassword() == null) {
            throw new IllegalStateException("CrossBorder email/password not configured");
        }
        String url = properties.getBaseUrl() + "/api/auth/login/password";
        Map<String, String> body = Map.of(
                "email", properties.getEmail(),
                "password", properties.getPassword()
        );
        String responseBody = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new CrossBorderApiException("CrossBorder login failed: " + res.getStatusCode(), res.getStatusCode());
                })
                .body(String.class);
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode success = root.path("success");
            if (!success.isMissingNode() && !success.asBoolean(true)) {
                throw new CrossBorderApiException("CrossBorder login error: " + root.path("error").asText(), (HttpStatusCode) null);
            }
            JsonNode data = root.path("data");
            accessToken = data.path("token").asText();
            long expiresIn = data.path("expiresIn").asLong(3_600_000L);
            tokenExpireAt = System.currentTimeMillis() + expiresIn;
            log.info("CrossBorder login succeeded, token expires in {} ms", expiresIn);
        } catch (CrossBorderApiException e) {
            throw e;
        } catch (Exception e) {
            throw new CrossBorderApiException("Failed to parse CrossBorder login response", e);
        }
    }

    public static class CrossBorderApiException extends RuntimeException {
        private final org.springframework.http.HttpStatusCode status;

        public CrossBorderApiException(String message, org.springframework.http.HttpStatusCode status) {
            super(message);
            this.status = status;
        }

        public CrossBorderApiException(String message, Throwable cause) {
            super(message, cause);
            this.status = null;
        }

        public org.springframework.http.HttpStatusCode getStatus() {
            return status;
        }
    }
}
