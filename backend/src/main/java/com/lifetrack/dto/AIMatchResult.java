package com.lifetrack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIMatchResult {
    private List<MatchDetail> matches;
    private String aiAnalysis;
    private Integer durationMinutes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchDetail {
        private Long subTaskId;
        private BigDecimal increment;
    }
}
