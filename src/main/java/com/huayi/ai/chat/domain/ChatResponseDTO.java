package com.huayi.ai.chat.domain;

/**
 * Author: YuJian
 * Create: 2025-02-20 15:48
 * Description:
 */
public record ChatResponseDTO(String answer) {
    public ChatResponseDTO {
    }

    @Override
    public String answer() {
        return answer;
    }

    @Override
    public String toString() {
        return "ChatResponseDTO{" +
                "answer='" + answer + '\'' +
                '}';
    }
}
