package com.huayi.ai.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Author: YuJian
 * Create: 2025-02-28 09:21
 * Description:
 */
@Component
public class MychatMemory implements ChatMemory {

    Map<String, List<Message>> conversationHistory = new ConcurrentHashMap<>();

    /**
     * 添加多条消息到指定会话的历史记录中
     * @param conversationId 会话唯一标识符
     * @param messages 要添加的消息集合（允许空集合但不会执行操作）
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        // 当会话不存在时创建线程安全的空列表，并将新消息批量追加到列表末尾
        this.conversationHistory.computeIfAbsent(conversationId, id -> Collections.synchronizedList(new ArrayList<>()))
                .addAll(messages);
    }

    /**
     * 添加单条消息到指定会话的历史记录中
     * @param conversationId 会话唯一标识符
     * @param message 要添加的单个消息对象（null值会导致NPE）
     */
    @Override
    public void add(String conversationId, Message message) {
        // 当会话不存在时创建线程安全的空列表，并将新消息追加到列表末尾
        this.conversationHistory.computeIfAbsent(conversationId, id -> Collections.synchronizedList(new ArrayList<>()))
                .add(message);
    }

    /**
     * 获取指定会话的最新N条消息记录
     * @param conversationId 会话唯一标识符
     * @param lastN 需要获取的历史消息数量（实际返回数量可能小于该值）
     * @return 包含最新消息的不可变列表（当无记录时返回空列表）
     */
    @Override
    public List<Message> get(String conversationId, int lastN) {
        List<Message> allMessages = conversationHistory.get(conversationId);
        // 处理空会话记录的特殊情况
        if (allMessages == null || allMessages.isEmpty()) {
            return List.of();
        }
        // 计算截取起始位置，确保不会越界。通过创建新列表隔离原始数据
        int start = Math.max(0, allMessages.size() - lastN);
        return new ArrayList<>(allMessages.subList(start, allMessages.size()));
    }

    /**
     * 清除指定会话的全部历史记录
     * @param conversationId 需要清除的会话唯一标识符
     */
    @Override
    public void clear(String conversationId) {
        // 直接移除整个会话的存储条目
        conversationHistory.remove(conversationId);
    }

}
