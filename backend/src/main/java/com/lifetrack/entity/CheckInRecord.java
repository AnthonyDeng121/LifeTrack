package com.lifetrack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "check_in_records", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_task_check_date",
        columnNames = {"user_id", "task_id", "check_in_date"}
))
public class CheckInRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(length = 20)
    private String mood;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (durationMinutes == null) durationMinutes = 0;
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
