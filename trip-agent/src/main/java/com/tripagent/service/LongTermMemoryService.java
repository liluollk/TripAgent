package com.tripagent.service;

import com.tripagent.model.entity.UserProfile;

/**
 * 长期记忆服务接口
 */
public interface LongTermMemoryService {

    /**
     * 获取或创建用户画像
     */
    UserProfile getOrCreateProfile(String userId);

    /**
     * 更新用户偏好
     */
    void updatePreferences(String userId, String preferences);

    /**
     * 添加访问城市
     */
    void addVisitedCity(String userId, String city);

    /**
     * 更新预算偏好
     */
    void updateBudgetPreference(String userId, double budget);

    /**
     * 获取记忆上下文
     */
    String getMemoryContext(String userId);
}
