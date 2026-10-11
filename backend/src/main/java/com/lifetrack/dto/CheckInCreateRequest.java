package com.lifetrack.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CheckInCreateRequest {
    @NotNull(message = "学习计划ID不能为空")
    private Long taskId;

    private LocalDate checkInDate;

    @NotBlank(message = "打卡内容不能为空")
    @Size(max = 500, message = "打卡内容不能超过500个字符")
    private String content;

    @Min(value = 0, message = "学习时长不能为负数")
    private Integer durationMinutes;

    @Size(max = 20, message = "心情描述不能超过20个字符")
    private String mood;
}
