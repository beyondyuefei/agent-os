package com.kuoge.agentos.channel.meta;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Meta Marketing API 接入配置（真实实现用；mode=mock 时不装配真实适配器）。
 */
@Component
@ConfigurationProperties(prefix = "agentos.channel.meta")
public class MetaProperties {

    /** mock | live */
    private String mode = "mock";
    private String baseUrl = "https://graph.facebook.com";
    private String apiVersion = "v21.0";
    private String accessToken = "";
    private String adAccountId = "";

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getAdAccountId() { return adAccountId; }
    public void setAdAccountId(String adAccountId) { this.adAccountId = adAccountId; }
}
