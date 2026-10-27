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
    @Schema(description = "已保存的原始学习记录ID，失败后使用此ID重新分析")
    private Long recordId;
    private boolean analysisUnavailable;
    private boolean matchingAmbiguous;
    @Schema(description = "本次持久化的步骤学习记录数，同一次输入可能对应多个步骤")
    private int savedRecordCount;
    @Schema(description = "本次新建的正式每日打卡数，重复学习不重复打卡")
    private int newCheckInCount;
    @Schema(description = "本次实际保存的学习时长，跨步骤和计划分摊后合计")
    private int durationMinutes;
    @Schema(description = "本次影响的任务更新明细")
    private List<TaskUpdateDetail> updates;
    
    @Schema(description = "AI 对该行为的总结分析", example = "干得漂亮！你已经掌握了 Web 开发的核心。")
    private String aiAnalysis;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskUpdateDetail {
        private Long taskId;
        private Long subTaskId;
        private String subTaskTitle;
        private boolean checkedInNow;
        private String taskTitle;
        private BigDecimal increment;
        private BigDecimal newTotalProgress;
    }
}
