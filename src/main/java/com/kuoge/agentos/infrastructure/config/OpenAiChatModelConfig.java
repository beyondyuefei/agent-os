package com.kuoge.agentos.infrastructure.config;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class OpenAiChatModelConfig {

    @Bean
    public OpenAiChatModel openAiChatModel(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.chat.options.model:k3-256k}") String model,
            @Value("${spring.ai.openai.base-url:https://api.kimi.com/coding/v1}") String baseUrl) {

        String effectiveKey = StringUtils.hasText(apiKey) ? apiKey : "dummy-key";

        var client = OpenAIOkHttpClient.builder()
                .apiKey(effectiveKey)
                .baseUrl(baseUrl)
                .build();

        // 同步/异步客户端都要显式给凭据：build() 会装配异步客户端，
        // 缺省时只认 OPENAI_API_KEY 环境变量，与项目使用的 KIMI_API_KEY 无关
        var asyncClient = OpenAIOkHttpClientAsync.builder()
                .apiKey(effectiveKey)
                .baseUrl(baseUrl)
                .build();

        var options = OpenAiChatOptions.builder()
                .model(model)
                .build();

        return OpenAiChatModel.builder()
                .openAiClient(client)
                .openAiClientAsync(asyncClient)
                .options(options)
                .build();
    }
}
