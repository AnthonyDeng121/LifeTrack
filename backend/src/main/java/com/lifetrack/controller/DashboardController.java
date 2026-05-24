package com.lifetrack.controller;

import com.lifetrack.common.Result;
import com.lifetrack.common.annotation.LoginRequired;
import com.lifetrack.dto.DashboardStatsResponse;
import com.lifetrack.dto.SubTaskContributionResponse;
import com.lifetrack.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "看板数据", description = "提供看板统计、时间分配、子任务贡献度等数据")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@LoginRequired
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "获取看板统计", description = "返回今日进度、时间分配饼图数据、周趋势图数据")
    @GetMapping("/stats")
    public Result<DashboardStatsResponse> getStats() {
        return Result.success(dashboardService.getStats());
    }

    @Operation(summary = "获取子任务贡献视图", description = "查看特定主任务下各子任务的动态贡献度")
    @GetMapping("/tasks/{taskId}/contributions")
    public Result<SubTaskContributionResponse> getTaskContribution(
            @Parameter(description = "主任务ID") @PathVariable Long taskId) {
        return Result.success(dashboardService.getTaskContribution(taskId));
    }
}
