package com.lifetrack.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@Schema(description = "看板统计数据响应")
public class DashboardStatsResponse {
    @Schema(description = "今日累计进度增量 (0-100)", example = "15.5")
    private BigDecimal todayTotalProgress;

    @Schema(description = "今日时间分配 (按分类统计时长)")
    private List<TimeDistribution> timeDistribution;

    @Schema(description = "周趋势数据 (过去7天每日进度增量)")
    private List<WeeklyTrend> weeklyTrend;

    @Schema(description = "当前焦虑等级 (1-10)", example = "5")
    private Integer currentAnxietyLevel;

    @Schema(description = "每日寄语", example = "你已经完成了67%的计划，晚上放心休息吧。")
    private String dailyQuote;

    @Schema(description = "情绪寄语", example = "别太焦虑，慢慢来，一切都会好起来的。")
    private String moodQuote;

    @Data
    @Builder
    public static class TimeDistribution {
        @Schema(description = "分类名称", example = "学习")
        private String name;
        @Schema(description = "投入时长 (分钟)", example = "120")
        private Integer minutes;
        @Schema(description = "占比 (0-100)", example = "45.0")
        private BigDecimal percentage;
        @Schema(description = "该分类下的具体行为明细")
        private List<BehaviorDetail> behaviors;
    }

    @Data
    @Builder
    public static class BehaviorDetail {
        @Schema(description = "行为名称/描述", example = "写了两个 Controller 接口")
        private String actionName;
        @Schema(description = "该行为投入时长 (分钟)", example = "45")
        private Integer minutes;
    }

    @Data
    @Builder
    public static class WeeklyTrend {
        @Schema(description = "日期", example = "5/7")
        private String date;
        @Schema(description = "当日进度增量", example = "20.0")
        private BigDecimal progress;
    }
}
