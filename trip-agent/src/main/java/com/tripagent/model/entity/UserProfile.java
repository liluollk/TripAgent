package com.tripagent.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_profile")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "preferences", columnDefinition = "TEXT")
    private String preferences;  // JSON format

    @Column(name = "budget_preference")
    private Double budgetPreference;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @PrePersist
    @PreUpdate
    public void prePersist() {
        lastActiveAt = LocalDateTime.now();
    }
}
