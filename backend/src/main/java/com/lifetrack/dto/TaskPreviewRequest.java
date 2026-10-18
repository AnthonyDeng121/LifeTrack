package com.lifetrack.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

/** 只生成草案，不写入计划；调整建议和上一版步骤作为模型上下文。 */
@Data
public class TaskPreviewRequest {
    @NotBlank @Size(max = 100)
    private String title;
    @Size(max = 1000)
    private String guidance;
    @Min(2) @Max(8)
    private Integer stepCount;
    @Size(max = 8)
    private List<@NotBlank @Size(max = 120) String> previousSteps;
}
