package com.lifetrack.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "行为同步请求")
public class ActionSyncRequest {
    @NotBlank(message = "行为描述不能为空")
    @Schema(description = "用户原始输入(自然语言)", example = "今天写了两个 Controller 接口并成功运行")
    private String rawInput;

    @Schema(description = "行为持续时长(模糊输入)", example = "30分钟 / 1小时 / 一会儿")
    private String durationInput;
}
