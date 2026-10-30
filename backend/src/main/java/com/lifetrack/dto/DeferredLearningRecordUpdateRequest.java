package com.lifetrack.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class DeferredLearningRecordUpdateRequest {
    @NotBlank(message = "学习内容不能为空")
    @Size(max = 500, message = "学习内容不能超过500字")
    private String content;
    @NotNull @Min(1) @Max(1440)
    private Integer durationMinutes;
}
