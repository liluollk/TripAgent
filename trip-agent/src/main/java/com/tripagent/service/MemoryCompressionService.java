package com.tripagent.service;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 记忆压缩服务接口
 */
public interface MemoryCompressionService {

    /**
     * 如果需要，压缩消息列表
     *
     * @param userId   用户ID
     * @param messages 原始消息列表
     * @return 压缩后的消息列表
     */
    List<Message> compressIfNeeded(String userId, List<Message> messages);
}
