package com.tripagent.service;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 聊天记忆服务接口
 */
public interface ChatMemoryService {

    /**
     * 添加用户消息
     */
    void addUserMessage(String conversationId, String content);

    /**
     * 添加助手消息
     */
    void addAssistantMessage(String conversationId, String content);

    /**
     * 获取对话历史
     */
    List<Message> getMessages(String conversationId);

    /**
     * 清除对话历史
     */
    void clear(String conversationId);

    /**
     * 事务性批量替换：清除旧消息并写入新消息（原子操作）
     * 用于记忆压缩场景，避免并发读取到空数据
     */
    void clearAndSaveAll(String conversationId, List<Message> messages);
}
