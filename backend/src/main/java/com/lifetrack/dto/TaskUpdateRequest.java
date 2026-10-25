package com.lifetrack.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class TaskUpdateRequest {
    @Size(min = 1, max = 100, message = "计划标题长度必须在1到100个字符之间")
    private String title;
    private String category;
    private LocalDate startDate;
    private LocalDate endDate;
    @Min(value = 1, message = "目标打卡天数至少为1天")
    private Integer targetDays;
    private String status;
    // 必须明确确认本次预览的记录，防止确认后新增的记录被意外删除。
    private Boolean discardOutsideCheckIns;
    private List<Long> confirmedDiscardCheckInIds;
}
