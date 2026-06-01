package com.lifetrack.controller;

import com.lifetrack.common.Result;
import com.lifetrack.common.annotation.LoginRequired;
import com.lifetrack.dto.*;
import com.lifetrack.service.ActionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "行为记录", description = "用户行为同步与 AI 贡献度分析")
@RestController
@RequestMapping("/api/v1/actions")
@RequiredArgsConstructor
@LoginRequired
public class ActionController {

    private final ActionService actionService;

    @Operation(summary = "同步行为记录", description = "用户输入自然语言描述，由 AI 判定贡献度并同步更新进度")
    @PostMapping("/sync")
    public Result<ActionSyncResponse> sync(@RequestBody @Validated ActionSyncRequest request) {
        return Result.success(actionService.syncAction(request));
    }

    @Operation(summary = "获取行为日志列表", description = "分页获取用户过往输入的自然语言记录及 AI 给出的贡献度评价")
    @GetMapping("/history")
    public Result<PageResponse<ActionHistoryResponse>> getHistory(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return Result.success(actionService.getHistory(pageable));
    }

    @Operation(summary = "删除/撤销记录", description = "用户输错内容或 AI 判定有误时，撤销该次进度更新")
    @DeleteMapping("/{actionId}")
    public Result<Void> deleteAction(
            @Parameter(description = "行为记录ID") @PathVariable Long actionId) {
        actionService.deleteAction(actionId);
        return Result.success();
    }
}
