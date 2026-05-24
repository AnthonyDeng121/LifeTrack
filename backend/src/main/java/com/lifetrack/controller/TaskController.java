package com.lifetrack.controller;

import com.lifetrack.common.Result;
import com.lifetrack.common.annotation.LoginRequired;
import com.lifetrack.dto.TaskDeconstructRequest;
import com.lifetrack.dto.TaskDeconstructResponse;
import com.lifetrack.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "任务管理", description = "提供任务拆解、AI 辅助等功能")
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@LoginRequired
public class TaskController {

    private final TaskService taskService;

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
