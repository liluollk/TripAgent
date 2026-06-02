package com.tripagent.agent.core;

import com.tripagent.service.TokenUsageTracker;
import com.tripagent.service.TokenUsageTracker.CallType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Token 使用量拦截 Advisor
 * 拦截所有 ChatClient 调用，记录 token 使用量
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenUsageAdvisor implements BaseAdvisor {

    private final TokenUsageTracker tokenUsageTracker;

    /**
     * ThreadLocal 传递当前调用类型
     */
    private static final ThreadLocal<CallType> CURRENT_CALL_TYPE = new ThreadLocal<>();

    /**
     * 设置当前调用类型（调用前设置）
     */
    public static void setCurrentCallType(CallType callType) {
        CURRENT_CALL_TYPE.set(callType);
    }

    /**
     * 清除当前调用类型（调用后清除）
     */
    public static void clearCurrentCallType() {
        CURRENT_CALL_TYPE.remove();
    }

    /**
     * 默认优先级，在 ChatModelCallAdvisor 之后执行（数值更大 = 更晚执行）
     */
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        // before 阶段不做处理，直接传递
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        try {
            if (response.chatResponse() != null && response.chatResponse().getMetadata() != null) {
                var usage = response.chatResponse().getMetadata().getUsage();
                if (usage != null) {
                    // 从 ThreadLocal 获取调用类型，默认为 PLANNING
                    CallType callType = CURRENT_CALL_TYPE.get();
                    if (callType == null) {
                        callType = CallType.PLANNING;
                    }
                    tokenUsageTracker.record(callType, usage);
                }
            }
        } catch (Exception e) {
            log.warn("记录 token 使用量失败: {}", e.getMessage());
        }
        // 注意：不在 finally 中 remove()，避免重试时 callType 丢失
        // 由调用方（ReActLoop）在操作完成后统一清除
        return response;
    }

    @Override
    public String getName() {
        return "TokenUsageAdvisor";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
