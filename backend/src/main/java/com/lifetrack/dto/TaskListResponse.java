package com.lifetrack.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "任务列表响应对象")
public class TaskListResponse {
    @Schema(description = "任务ID", example = "101")
    private Long id;

    @Schema(description = "任务标题", example = "学会 Spring Boot 开发")
    private String title;

    @Schema(description = "总进度 (0-100)", example = "67.5")
    private BigDecimal totalProgress;

    @Schema(description = "任务分类", example = "学习")
    private String category;
}
