package com.lifetrack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_profiles")
public class UserProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "energy_level")
    private Integer energyLevel;

    @Column(name = "daily_quote", length = 500)
    private String dailyQuote;

    @Column(name = "interest_tags", columnDefinition = "JSON")
    private String interestTags; // 存储为 JSON 字符串

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (energyLevel == null) {
            energyLevel = 100;
        }
    }
}
