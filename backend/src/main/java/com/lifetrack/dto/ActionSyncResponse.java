package com.lifetrack.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "行为同步响应")
public class ActionSyncResponse {
    @Schema(description = "本次影响的任务更新明细")
    private List<TaskUpdateDetail> updates;
    
    @Schema(description = "AI 对该行为的总结分析", example = "干得漂亮！你已经掌握了 Web 开发的核心。")
    private String aiAnalysis;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskUpdateDetail {
        private String taskTitle;
        private BigDecimal increment;
        private BigDecimal newTotalProgress;
    }
}
