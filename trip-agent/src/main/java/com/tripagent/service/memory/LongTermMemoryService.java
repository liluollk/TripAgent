package com.tripagent.service.memory;

import com.tripagent.agent.core.AgentContext;
import com.tripagent.agent.core.TokenUsageAdvisor;
import com.tripagent.model.entity.UserLongTermMemory;
import com.tripagent.repository.memory.UserLongTermMemoryRepository;
import com.tripagent.service.TokenUsageTracker.CallType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class LongTermMemoryService {

    private final ChatClient chatClient;
    private final UserLongTermMemoryRepository memoryRepository;
    private final ObjectMapper objectMapper;

    public LongTermMemoryService(
            @Qualifier("planningChatModel") ChatModel chatModel,
            UserLongTermMemoryRepository memoryRepository,
            TokenUsageAdvisor tokenUsageAdvisor,
            ObjectMapper objectMapper) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(tokenUsageAdvisor)
                .build();
        this.memoryRepository = memoryRepository;
        this.objectMapper = objectMapper;
    }

    private static final String EXTRACTION_PROMPT = """
        从以下对话中提取用户的长期记忆信息，包括：
        1. PREFERENCE: 用户偏好（喜欢什么、不喜欢什么）
        2. FACT: 事实信息（去过哪里、职业等）
        3. CONSTRAINT: 约束条件（预算限制、时间限制、特殊需求等）

        对话内容：
        %s

        请以 JSON 数组格式输出，每个记忆项包含 type 和 content 字段：
        [{"type": "PREFERENCE", "content": "用户喜欢自然风光"}, {"type": "CONSTRAINT", "content": "预算不超过5000元"}]

        如果没有可提取的记忆，返回空数组：[]
        """;

    /**
     * 从对话中提取长期记忆
     * @param conversationId 会话ID
     * @param messages 消息列表
     */
    public void extractMemories(String conversationId, List<AgentContext.ChatMessage> messages) {
        log.info("开始从对话 {} 提取长期记忆", conversationId);

        String messageText = formatMessages(messages);
        String prompt = String.format(EXTRACTION_PROMPT, messageText);

        try {
            TokenUsageAdvisor.setCurrentCallType(CallType.LONG_TERM_MEMORY);
            String result = chatClient.prompt(new Prompt(new UserMessage(prompt)))
                    .call()
                    .chatResponse()
                    .getResult()
                    .getOutput()
                    .getText();

            // 解析 JSON 结果
            List<UserLongTermMemory> memories = parseMemories(conversationId, result);

            // 保存到数据库
            for (UserLongTermMemory memory : memories) {
                memoryRepository.save(memory);
                log.info("保存长期记忆: {} - {}", memory.getMemoryType(), memory.getContent());
            }

            log.info("从对话 {} 提取了 {} 条长期记忆", conversationId, memories.size());
        } catch (Exception e) {
            log.error("提取长期记忆失败: {}", e.getMessage(), e);
        } finally {
            TokenUsageAdvisor.clearCurrentCallType();
        }
    }

    /**
     * 获取用户的所有长期记忆
     */
    public List<UserLongTermMemory> getUserMemories(String conversationId) {
        return memoryRepository.findByConversationId(conversationId);
    }

    /**
     * 获取特定类型的用户记忆
     */
    public List<UserLongTermMemory> getUserMemoriesByType(String conversationId, String memoryType) {
        return memoryRepository.findByConversationIdAndType(conversationId, memoryType);
    }

    /**
     * 格式化用户记忆为提示词上下文
     */
    public String formatMemoriesForPrompt(String conversationId) {
        List<UserLongTermMemory> memories = getUserMemories(conversationId);
        if (memories.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("\n【用户历史偏好】\n");
        for (UserLongTermMemory memory : memories) {
            sb.append("- ").append(memory.getMemoryType())
              .append(": ").append(memory.getContent()).append("\n");
        }
        return sb.toString();
    }

    private List<UserLongTermMemory> parseMemories(String conversationId, String json) {
        List<UserLongTermMemory> memories = new ArrayList<>();

        try {
            // 从 LLM 输出中提取 JSON 数组
            String arrayJson = json;
            int start = json.indexOf("[");
            int end = json.lastIndexOf("]");
            if (start >= 0 && end > start) {
                arrayJson = json.substring(start, end + 1);
            }

            JsonNode arrayNode = objectMapper.readTree(arrayJson);
            if (arrayNode.isArray()) {
                for (JsonNode node : arrayNode) {
                    UserLongTermMemory memory = new UserLongTermMemory();
                    memory.setConversationId(conversationId);
                    memory.setMemoryType(node.has("type") ? node.get("type").asText() : "FACT");
                    memory.setContent(node.has("content") ? node.get("content").asText() : "");
                    memory.setConfidence(0.8);
                    memory.setCreatedAt(LocalDateTime.now());
                    memories.add(memory);
                }
            }
        } catch (Exception e) {
            log.warn("解析长期记忆 JSON 失败，回退到正则解析: {}", e.getMessage());
            // 回退到正则解析
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "\\{\\s*\"type\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"content\"\\s*:\\s*\"([^\"]+)\"\\s*\\}"
            );
            java.util.regex.Matcher matcher = pattern.matcher(json);
            while (matcher.find()) {
                UserLongTermMemory memory = new UserLongTermMemory();
                memory.setConversationId(conversationId);
                memory.setMemoryType(matcher.group(1));
                memory.setContent(matcher.group(2));
                memory.setConfidence(0.8);
                memory.setCreatedAt(LocalDateTime.now());
                memories.add(memory);
            }
        }

        return memories;
    }

    private String formatMessages(List<AgentContext.ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (AgentContext.ChatMessage msg : messages) {
            String role = "user".equals(msg.getRole()) ? "用户" : "助手";
            sb.append(role).append(": ").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }
}
