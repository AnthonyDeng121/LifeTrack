package com.lifetrack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInResponse {
    private Long id;
    private Long taskId;
    private String taskTitle;
    private LocalDate checkInDate;
    private String content;
    private Integer durationMinutes;
    private String mood;
    private LocalDateTime createdAt;
}
