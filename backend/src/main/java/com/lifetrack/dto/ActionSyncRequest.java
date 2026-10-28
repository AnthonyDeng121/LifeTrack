package com.lifetrack.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "行为同步请求")
public class ActionSyncRequest {
    @NotBlank(message = "缺少提交标识，请刷新页面")
    @jakarta.validation.constraints.Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", message = "提交标识格式错误")
    private String requestId;
    @jakarta.validation.constraints.NotNull(message = "请选择学习计划")
    @Schema(description = "学习记录所属计划")
    private Long taskId;
    @NotBlank(message = "行为描述不能为空")
    @jakarta.validation.constraints.Size(max = 500, message = "学习记录不能超过500字")
    @Schema(description = "用户原始输入(自然语言)", example = "今天写了两个 Controller 接口并成功运行")
    private String rawInput;

    @jakarta.validation.constraints.NotNull(message = "请填写学习时长")
    @jakarta.validation.constraints.Min(value = 1, message = "学习时长至少1分钟")
    @jakarta.validation.constraints.Max(value = 1440, message = "单条学习记录不能超过24小时")
    @Schema(description = "用户明确填写的整数分钟，不使用AI估算时长", example = "90")
    private Integer durationMinutes;
}
