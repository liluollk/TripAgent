package com.tripagent.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户访问城市记录
 */
@Entity
@Table(name = "user_visited_city", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "city"})
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserVisitedCity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "visited_at")
    private LocalDateTime visitedAt;

    @PrePersist
    public void prePersist() {
        visitedAt = LocalDateTime.now();
    }
}
