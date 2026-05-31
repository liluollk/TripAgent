package com.tripagent.service.impl;

import com.tripagent.model.entity.UserProfile;
import com.tripagent.model.entity.UserVisitedCity;
import com.tripagent.repository.UserProfileRepository;
import com.tripagent.repository.UserVisitedCityRepository;
import com.tripagent.service.LongTermMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 长期记忆服务实现
 */
@Slf4j
@Service
public class LongTermMemoryServiceImpl implements LongTermMemoryService {

    private final UserProfileRepository userProfileRepository;
    private final UserVisitedCityRepository userVisitedCityRepository;

    public LongTermMemoryServiceImpl(
            UserProfileRepository userProfileRepository,
            UserVisitedCityRepository userVisitedCityRepository) {
        this.userProfileRepository = userProfileRepository;
        this.userVisitedCityRepository = userVisitedCityRepository;
    }

    @Override
    public UserProfile getOrCreateProfile(String userId) {
        return userProfileRepository.findById(userId)
                .orElseGet(() -> {
                    UserProfile profile = new UserProfile();
                    profile.setUserId(userId);
                    return userProfileRepository.save(profile);
                });
    }

    @Override
    public void updatePreferences(String userId, String preferences) {
        UserProfile profile = getOrCreateProfile(userId);
        profile.setPreferences(preferences);
        userProfileRepository.save(profile);
    }

    @Override
    @Transactional
    public void addVisitedCity(String userId, String city) {
        // 使用关联表检查是否已存在
        if (!userVisitedCityRepository.existsByUserIdAndCity(userId, city)) {
            UserVisitedCity visitedCity = new UserVisitedCity();
            visitedCity.setUserId(userId);
            visitedCity.setCity(city);
            userVisitedCityRepository.save(visitedCity);
            log.info("记录用户访问城市: userId={}, city={}", userId, city);
        }
    }

    @Override
    public void updateBudgetPreference(String userId, double budget) {
        UserProfile profile = getOrCreateProfile(userId);
        profile.setBudgetPreference(budget);
        userProfileRepository.save(profile);
    }

    @Override
    public String getMemoryContext(String userId) {
        UserProfile profile = getOrCreateProfile(userId);
        StringBuilder context = new StringBuilder();

        if (profile.getPreferences() != null && !profile.getPreferences().isEmpty()) {
            context.append("用户偏好: ").append(profile.getPreferences()).append("\n");
        }

        // 从关联表查询访问过的城市
        List<UserVisitedCity> visitedCities = userVisitedCityRepository.findByUserIdOrderByVisitedAtDesc(userId);
        if (!visitedCities.isEmpty()) {
            String cities = visitedCities.stream()
                    .map(UserVisitedCity::getCity)
                    .collect(Collectors.joining(", "));
            context.append("历史访问城市: ").append(cities).append("\n");
        }

        if (profile.getBudgetPreference() != null) {
            context.append("预算偏好: ").append(profile.getBudgetPreference()).append("元\n");
        }

        return context.length() > 0 ? context.toString() : "新用户，无历史记录";
    }
}
