package com.kuoge.agentos.infrastructure.config;

import com.openai.client.okhttp.OpenAIOkHttpClient;
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
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String model,
            @Value("${spring.ai.openai.base-url:https://api.openai.com/v1}") String baseUrl) {

        String effectiveKey = StringUtils.hasText(apiKey) ? apiKey : "dummy-key";

        var client = OpenAIOkHttpClient.builder()
                .apiKey(effectiveKey)
                .baseUrl(baseUrl)
                .build();

        var options = OpenAiChatOptions.builder()
                .model(model)
                .build();

        return OpenAiChatModel.builder()
                .openAiClient(client)
                .options(options)
                .build();
    }
}
