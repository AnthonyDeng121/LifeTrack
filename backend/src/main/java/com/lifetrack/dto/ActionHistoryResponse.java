package com.lifetrack.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "行为记录历史项")
public class ActionHistoryResponse {
    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "原始输入")
    private String rawInput;

    @Schema(description = "贡献度百分比")
    private BigDecimal contribution;

    @Schema(description = "AI分析建议")
    private String aiAnalysis;

    @Schema(description = "所属任务名称")
    private String taskTitle;

    @Schema(description = "行为类型")
    private String category;

    @Schema(description = "记录时间")
    private LocalDateTime createdAt;
}
