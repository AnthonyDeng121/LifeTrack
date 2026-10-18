package com.lifetrack.controller;

import com.lifetrack.common.Result;
import com.lifetrack.common.annotation.LoginRequired;
import com.lifetrack.dto.*;
import com.lifetrack.service.DeferredLearningRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

@RestController @RequiredArgsConstructor @LoginRequired
@RequestMapping("/api/v1/actions/deferred")
public class DeferredLearningRecordController {
    private final DeferredLearningRecordService service;
    @PostMapping public Result<DeferredLearningRecordResponse> save(@RequestBody @Valid DeferredLearningRecordRequest request) {
        return Result.success(service.save(request));
    }
    @GetMapping public Result<List<DeferredLearningRecordResponse>> list() { return Result.success(service.list()); }
    @PostMapping("/{id}/retry") public Result<ActionSyncResponse> retry(@PathVariable Long id) {
        return Result.success(service.retry(id));
    }
    @PostMapping("/import-legacy") public Result<Integer> importLegacy() { return Result.success(service.importLegacyCheckIns()); }
}
