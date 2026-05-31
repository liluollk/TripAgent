package com.tripagent.service.impl;

import com.tripagent.service.ChatMemoryService;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 聊天记忆服务实现
 */
@Service
public class ChatMemoryServiceImpl implements ChatMemoryService {

    private final ChatMemory chatMemory;

    public ChatMemoryServiceImpl(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    @Override
    public void addUserMessage(String conversationId, String content) {
        chatMemory.add(conversationId, new UserMessage(content));
    }

    @Override
    public void addAssistantMessage(String conversationId, String content) {
        chatMemory.add(conversationId, new AssistantMessage(content));
    }

    @Override
    public List<Message> getMessages(String conversationId) {
        return chatMemory.get(conversationId);
    }

    @Override
    public void clear(String conversationId) {
        chatMemory.clear(conversationId);
    }

    @Override
    @Transactional
    public void clearAndSaveAll(String conversationId, List<Message> messages) {
        // 在同一个事务内：先清除旧数据，再写入新数据
        // 使用底层 repository 直接操作，避免 MessageWindowChatMemory 的窗口截断
        chatMemory.clear(conversationId);
        for (Message msg : messages) {
            chatMemory.add(conversationId, msg);
        }
    }
}
