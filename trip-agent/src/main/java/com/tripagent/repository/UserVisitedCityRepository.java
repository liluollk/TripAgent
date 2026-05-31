package com.tripagent.repository;

import com.tripagent.model.entity.UserVisitedCity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserVisitedCityRepository extends JpaRepository<UserVisitedCity, Long> {

    List<UserVisitedCity> findByUserIdOrderByVisitedAtDesc(String userId);

    boolean existsByUserIdAndCity(String userId, String city);
}
