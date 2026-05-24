package com.lifetrack.controller;

import com.lifetrack.common.Result;
import com.lifetrack.common.annotation.LoginRequired;
import com.lifetrack.dto.ActionSyncRequest;
import com.lifetrack.dto.ActionSyncResponse;
import com.lifetrack.service.ActionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
