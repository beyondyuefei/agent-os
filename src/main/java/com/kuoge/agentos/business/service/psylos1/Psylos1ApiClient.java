package com.kuoge.agentos.business.service.psylos1;

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
 * Psylos1 管理后台 API 客户端。
 *
 * <p>统一处理登录、Token 刷新、GET/POST 请求和统一响应包装。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Psylos1ApiClient {

    private final Psylos1Properties properties;
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
        } catch (Psylos1ApiException e) {
            // Token 失效时重试一次
            if (e.getStatus() != null && e.getStatus().isSameCodeAs(org.springframework.http.HttpStatus.UNAUTHORIZED)) {
                log.warn("Psylos1 token may expired, re-login and retry once");
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
                        throw new Psylos1ApiException("Psylos1 POST " + path + " failed: " + res.getStatusCode(),
                                res.getStatusCode());
                    })
                    .body(String.class);
        } else {
            responseBody = restClient.get()
                    .uri(url, builder -> addParams(builder, params))
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new Psylos1ApiException("Psylos1 GET " + path + " failed: " + res.getStatusCode(),
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
                throw new Psylos1ApiException("Psylos1 " + path + " error: " + error, (HttpStatusCode) null);
            }
            return root.path("data");
        } catch (Psylos1ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new Psylos1ApiException("Failed to parse Psylos1 response for " + path, e);
        }
    }

    private synchronized void ensureToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireAt - 60_000) {
            return;
        }
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Psylos1 client is disabled");
        }
        if (properties.getEmail() == null || properties.getPassword() == null) {
            throw new IllegalStateException("Psylos1 email/password not configured");
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
                    throw new Psylos1ApiException("Psylos1 login failed: " + res.getStatusCode(), res.getStatusCode());
                })
                .body(String.class);
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode success = root.path("success");
            if (!success.isMissingNode() && !success.asBoolean(true)) {
                throw new Psylos1ApiException("Psylos1 login error: " + root.path("error").asText(), (HttpStatusCode) null);
            }
            JsonNode data = root.path("data");
            accessToken = data.path("token").asText();
            long expiresIn = data.path("expiresIn").asLong(3_600_000L);
            tokenExpireAt = System.currentTimeMillis() + expiresIn;
            log.info("Psylos1 login succeeded, token expires in {} ms", expiresIn);
        } catch (Psylos1ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new Psylos1ApiException("Failed to parse Psylos1 login response", e);
        }
    }

    public static class Psylos1ApiException extends RuntimeException {
        private final org.springframework.http.HttpStatusCode status;

        public Psylos1ApiException(String message, org.springframework.http.HttpStatusCode status) {
            super(message);
            this.status = status;
        }

        public Psylos1ApiException(String message, Throwable cause) {
            super(message, cause);
            this.status = null;
        }

        public org.springframework.http.HttpStatusCode getStatus() {
            return status;
        }
    }
}
