package com.huayi.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Author: YuJian
 * Create: 2025-02-28 09:43
 * Description:
 */
@Configuration
public class ChatConfiguration {
    @Bean
    public ChatClient myChatClient(OllamaChatModel ollamaChatModel, MychatMemory mychatMemory) {
        return ChatClient.builder(ollamaChatModel).defaultSystem(
                        """
                                你是一家名叫“xx信息科技”的知识库文档助手。
                                """
                )
                .defaultAdvisors(new PromptChatMemoryAdvisor(mychatMemory))
                .build();
    }
}
