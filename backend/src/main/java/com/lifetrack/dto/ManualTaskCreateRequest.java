package com.lifetrack.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Data
@Schema(description = "手动创建任务请求")
public class ManualTaskCreateRequest {
    @NotBlank(message = "任务标题不能为空")
    @jakarta.validation.constraints.Size(max = 100, message = "计划标题不能超过100个字符")
    @Schema(description = "主任务标题", example = "手动完成数据库课设")
    private String title;

    @NotBlank(message = "任务分类不能为空")
    @Schema(description = "任务分类", example = "学习")
    private String category;

    @NotNull(message = "计划开始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "计划结束日期不能为空")
    private LocalDate endDate;

    @NotNull(message = "目标打卡天数不能为空")
    @Min(value = 1, message = "目标打卡天数至少为1天")
    private Integer targetDays;

    @NotEmpty(message = "子任务列表不能为空")
    @Schema(description = "自定义子任务列表")
    private List<SubTaskDTO> subTasks;

    @Data
    public static class SubTaskDTO {
        @NotBlank(message = "子任务内容不能为空")
        @Schema(description = "子任务内容", example = "完成数据库表设计")
        private String content;

        @Schema(description = "子任务权重 (0-1)", example = "0.3")
        private BigDecimal weight;
    }
}
