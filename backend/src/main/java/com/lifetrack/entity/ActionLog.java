package com.lifetrack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "action_logs")
public class ActionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "sub_task_id")
    private Long subTaskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Task.Category category;

    @Column(name = "duration_minutes")
    private Integer durationMinutes; // 投入时长（分钟）

    @Column(name = "raw_input", columnDefinition = "TEXT", nullable = false)
    private String rawInput;

    private BigDecimal contribution;

    @Column(name = "ai_analysis")
    private String aiAnalysis;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (contribution == null) {
            contribution = BigDecimal.ZERO;
        }
    }
}
