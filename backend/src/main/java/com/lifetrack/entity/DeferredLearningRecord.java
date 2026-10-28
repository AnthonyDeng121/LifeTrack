package com.lifetrack.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "deferred_learning_records")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DeferredLearningRecord {
    public enum Status { PENDING, APPLIED, UNMATCHED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 36) private String requestId;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private Long taskId;
    @Column(nullable = false, length = 500) private String content;
    @Column(nullable = false) private Integer durationMinutes;
    @Column(nullable = false) private LocalDateTime recordedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status;
    @Column(columnDefinition = "TEXT") private String lastAnalysis;
}
