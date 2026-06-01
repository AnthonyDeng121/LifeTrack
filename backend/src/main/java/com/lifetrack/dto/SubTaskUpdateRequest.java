package com.lifetrack.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "子任务更新请求")
public class SubTaskUpdateRequest {

    @Schema(description = "子任务内容", example = "新任务内容")
    private String content;

    @DecimalMin(value = "0.01", message = "权重最小为 0.01")
    @DecimalMax(value = "1.00", message = "权重最大为 1.00")
    @Schema(description = "子任务权重 (0.01-1.00)", example = "0.30")
    private BigDecimal weight;
}
