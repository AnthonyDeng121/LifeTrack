package com.lifetrack.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class SubTaskCreateRequest {
    @NotBlank(message = "子计划内容不能为空")
    @Size(max = 255, message = "子计划内容不能超过255字")
    private String content;
    @NotNull(message = "请填写子计划权重")
    @DecimalMin(value = "0.01", message = "权重不能小于1%")
    @DecimalMax(value = "1", message = "权重不能超过100%")
    private BigDecimal weight;
}
