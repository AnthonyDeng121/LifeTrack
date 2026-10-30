package com.lifetrack.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;
import java.time.LocalDate;

/** 只生成草案，不写入计划；调整建议和上一版步骤作为模型上下文。 */
@Data
public class TaskPreviewRequest {
    @NotBlank @Size(max = 100)
    private String title;
    @NotNull(message = "计划开始日期不能为空")
    private LocalDate startDate;
    @NotNull(message = "计划结束日期不能为空")
    private LocalDate endDate;
    @NotNull(message = "目标打卡天数不能为空") @Min(1)
    private Integer targetDays;
    @Size(max = 1000)
    private String guidance;
    @Min(2) @Max(10)
    private Integer stepCount;
    @Size(max = 10)
    private List<@NotBlank @Size(max = 120) String> previousSteps;
}
