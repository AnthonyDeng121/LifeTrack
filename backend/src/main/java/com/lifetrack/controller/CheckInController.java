package com.lifetrack.controller;

import com.lifetrack.common.Result;
import com.lifetrack.common.annotation.LoginRequired;
import com.lifetrack.dto.CheckInCreateRequest;
import com.lifetrack.dto.CheckInResponse;
import com.lifetrack.dto.CheckInStatsResponse;
import com.lifetrack.service.CheckInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "学习打卡", description = "正式每日打卡、历史查询与打卡统计")
@RestController
@RequestMapping("/api/v1/check-ins")
@RequiredArgsConstructor
@LoginRequired
public class CheckInController {
    private final CheckInService checkInService;

    @Operation(summary = "完成一次正式打卡")
    @PostMapping
    public Result<CheckInResponse> create(@RequestBody @Valid CheckInCreateRequest request) {
        return Result.success(checkInService.create(request));
    }

    @Operation(summary = "查询打卡历史", description = "可按计划和日期范围筛选")
    @GetMapping
    public Result<List<CheckInResponse>> history(
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return Result.success(checkInService.history(taskId, startDate, endDate));
    }

    @Operation(summary = "获取指定计划的打卡统计")
    @GetMapping("/tasks/{taskId}/stats")
    public Result<CheckInStatsResponse> stats(@PathVariable Long taskId) {
        return Result.success(checkInService.stats(taskId));
    }
}
