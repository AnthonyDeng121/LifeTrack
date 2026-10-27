package com.lifetrack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDetailResponse {
    private Long id;
    private Long version;
    private long learningDurationMinutes;
    private String title;
    private String category;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer targetDays;
    private String status;
    private String statusDescription;
    private BigDecimal totalProgress;
    private String aiSuggestion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
