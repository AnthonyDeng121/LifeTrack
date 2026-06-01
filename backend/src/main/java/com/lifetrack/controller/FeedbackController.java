package com.lifetrack.controller;

import com.lifetrack.common.Result;
import com.lifetrack.common.annotation.LoginRequired;
import com.lifetrack.dto.FeedbackReportResponse;
import com.lifetrack.dto.UserMoodUpdateRequest;
import com.lifetrack.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "情绪反馈系统", description = "提供 AI 深度报告与用户情绪状态管理")
@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
@LoginRequired
public class FeedbackController {

    private final FeedbackService feedbackService;

    @Operation(summary = "获取周/月报分析", description = "基于用户近期的行为日志，由 AI 生成深度成就总结与展望")
    @GetMapping("/report")
    public Result<FeedbackReportResponse> getReport() {
        return Result.success(feedbackService.getFeedbackReport());
    }

    @Operation(summary = "情绪状态更新", description = "记录用户当下的焦虑程度 (1-10)，供 AI 调整后续的激励策略与反馈语气")
    @PostMapping("/mood")
    public Result<Void> updateMood(@RequestBody @Validated UserMoodUpdateRequest request) {
        feedbackService.updateMood(request);
        return Result.success();
    }
}
