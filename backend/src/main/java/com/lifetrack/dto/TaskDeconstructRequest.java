package com.lifetrack.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "任务拆解请求对象")
public class TaskDeconstructRequest {
    @NotBlank(message = "任务标题不能为空")
    @Size(max = 100, message = "任务标题长度不能超过100个字符")
    @Schema(description = "任务标题", example = "学习 Spring Boot")
    private String title;

    @NotBlank(message = "任务分类不能为空")
    @Schema(description = "任务分类", example = "学习")
    private String category;
}
