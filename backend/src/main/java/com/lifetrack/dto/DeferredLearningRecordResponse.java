package com.lifetrack.dto;

import com.lifetrack.entity.DeferredLearningRecord;
import java.time.LocalDateTime;

public record DeferredLearningRecordResponse(Long id, Long taskId, String taskTitle, String content,
        Integer durationMinutes, LocalDateTime recordedAt, DeferredLearningRecord.Status status, String lastAnalysis,
        boolean canRetry, String retryBlockedReason) {}
