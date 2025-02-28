package com.huayi.ai.chat.controller;

import com.huayi.ai.chat.domain.ChatResponseDTO;
import com.huayi.ai.chat.service.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * Author: YuJian
 * Create: 2025-02-20 16:08
 * Description:
 */
@RestController
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponseDTO> chat(@RequestBody String query) {
        ChatResponse response = chatService.chat(query);

        if (response == null || response.getResult() == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ChatResponseDTO("网络繁忙"));
        }
        String answer = response.getResult().getOutput().getText();
        return ResponseEntity.ok(new ChatResponseDTO(answer));
    }

    @PostMapping("/streamChat")
    public Flux<ChatResponse> streamChat(@RequestBody String query) {
        return chatService.streamChat(query);
    }
}
