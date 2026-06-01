package com.lifetrack.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "用户情绪状态更新请求")
public class UserMoodUpdateRequest {
    @NotNull(message = "焦虑等级不能为空")
    @Min(value = 1, message = "焦虑等级最小为1")
    @Max(value = 10, message = "焦虑等级最大为10")
    @Schema(description = "当前焦虑等级 (1-10，1为放松，10为极度焦虑)", example = "7")
    private Integer anxietyLevel;
}
