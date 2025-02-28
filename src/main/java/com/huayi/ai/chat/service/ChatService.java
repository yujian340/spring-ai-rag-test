package com.huayi.ai.chat.service;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;


/**
 * Author: YuJian
 * Create: 2025-02-20 15:54
 * Description:
 */
@Service
public class ChatService {

    @Resource
    private OllamaChatModel chatModel;

    @Resource
    private ChatClient myChatClient;

    public ChatResponse chat(String query) {
        return chatModel.call(new Prompt(query));
    }

    public Flux<ChatResponse> streamChat(String query) {
        return myChatClient.prompt()
                .user(query)
                .advisors(adv -> adv
                        // 设置检索的上下文记录条数
                        .param(AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100)
                        // 指定会话唯一标识，用于区分不同的用户对话
                        .param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, 1))
                .stream().chatResponse();
    }
}
