package com.lifetrack.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@Schema(description = "子任务贡献度视图响应")
public class SubTaskContributionResponse {
    @Schema(description = "主任务标题", example = "学会 Spring Boot 开发")
    private String taskTitle;

    @Schema(description = "总进度", example = "65.0")
    private BigDecimal totalProgress;

    @Schema(description = "子任务贡献详情")
    private List<SubTaskContribution> subTasks;

    @Data
    @Builder
    public static class SubTaskContribution {
        @Schema(description = "子任务ID", example = "1")
        private Long id;
        @Schema(description = "子任务内容", example = "环境搭建")
        private String content;
        @Schema(description = "权重 (0.00-1.00)", example = "0.20")
        private BigDecimal weight;
        @Schema(description = "当前完成进度 (0-100)", example = "50.0")
        private BigDecimal currentProgress;
        @Schema(description = "对主任务的总贡献百分点", example = "10.0")
        private BigDecimal totalContributionToMainTask;
    }
}
