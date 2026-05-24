package com.lifetrack.controller;

import com.lifetrack.common.Result;
import com.lifetrack.common.annotation.LoginRequired;
import com.lifetrack.dto.SubTaskListResponse;
import com.lifetrack.dto.TaskDeconstructRequest;
import com.lifetrack.dto.TaskDeconstructResponse;
import com.lifetrack.dto.TaskListResponse;
import com.lifetrack.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "任务管理", description = "提供任务拆解、AI 辅助等功能")
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@LoginRequired
public class TaskController {

    private final TaskService taskService;

    /**
     * 获取任务列表
     * 获取当前用户所有进行中的任务及其总进度
     */
    @Operation(summary = "获取任务列表", description = "获取当前用户所有进行中的任务及其总进度")
    @GetMapping
    public Result<List<TaskListResponse>> getTaskList() {
        List<TaskListResponse> response = taskService.getTaskList();
        return Result.success(response);
    }

    /**
     * 获取子任务列表
     * 获取指定主任务下的所有拆解后的子任务
     */
    @Operation(summary = "获取子任务列表", description = "获取指定主任务下的所有子任务")
    @GetMapping("/{taskId}/subtasks")
    public Result<List<SubTaskListResponse>> getSubTaskList(
            @Parameter(description = "主任务ID") @PathVariable Long taskId) {
        List<SubTaskListResponse> response = taskService.getSubTaskList(taskId);
        return Result.success(response);
    }

    /**
     * 任务拆解接口 (AI 驱动)
     * 用户输入一个宏观目标，AI 自动生成子任务并存入数据库
     */
    @Operation(summary = "任务拆解", description = "使用 AI 将宏观目标拆解为具体子任务")
    @PostMapping("/deconstruct")
    public Result<TaskDeconstructResponse> deconstruct(@RequestBody @Validated TaskDeconstructRequest request) {
        TaskDeconstructResponse response = taskService.deconstructTask(request);
        return Result.success(response);
    }
}
