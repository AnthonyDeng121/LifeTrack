package com.lifetrack.service;

import com.lifetrack.dto.*;
import com.lifetrack.entity.*;
import java.time.LocalDateTime;
import java.util.*;

/** 仅在服务间传递的 AI 分析快照，不能从 HTTP 请求反序列化。 */
public record PreparedLearningRecord(ActionSyncRequest request, LocalDateTime recordedAt, Integer fixedDuration,
                                     Long userId, Map<Long, String> plans, Map<Long, String> steps,
                                     Map<Long, Long> stepPlans, AIMatchResult analysis) {
    public static String state(Task t) {
        return Arrays.asList(t.getVersion(), t.getTitle(), t.getCategory(), t.getStatus(), t.getStartDate(),
                t.getEndDate(), t.getTargetDays(), t.getTotalProgress()).toString();
    }
    public static String state(SubTask s) {
        return Arrays.asList(s.getTaskId(), s.getContent(), s.getWeight(), s.getCurrentProgress(),
                s.getIsCompleted(), s.getOrderNum()).toString();
    }
}
