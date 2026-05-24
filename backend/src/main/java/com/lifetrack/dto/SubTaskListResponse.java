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
@Schema(description = "子任务列表响应对象")
public class SubTaskListResponse {
    @Schema(description = "子任务ID", example = "1")
    private Long id;

    @Schema(description = "子任务内容", example = "环境搭建与 Maven 使用")
    private String content;

    @Schema(description = "权重 (0-100)", example = "15.0")
    private BigDecimal weight;

    @Schema(description = "当前进度 (0-100)", example = "0.0")
    private BigDecimal currentProgress;

    @Schema(description = "是否完成 (0-否, 1-是)", example = "0")
    private Integer isCompleted;
}
