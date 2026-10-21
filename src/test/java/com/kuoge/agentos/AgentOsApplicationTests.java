package com.kuoge.agentos;

import com.kuoge.agentos.orchestration.MarketplaceOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@TestPropertySource(properties = "spring.ai.openai.api-key=dummy-key-for-test")
class AgentOsApplicationTests {

    @MockBean
    private ChatModel chatModel;

    @Autowired
    private MarketplaceOrchestrator orchestrator;

    @Test
    void contextLoads() {
        assertNotNull(orchestrator);
    }
}
