package com.lifetrack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInStatsResponse {
    private Long taskId;
    private String taskTitle;
    private Integer targetDays;
    private Integer checkedInDays;
    private Integer currentStreak;
    private Integer longestStreak;
    private BigDecimal completionRate;
    private List<DailyTrend> recentSevenDays;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyTrend {
        private LocalDate date;
        private Boolean checkedIn;
        private Integer durationMinutes;
    }
}
