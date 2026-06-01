package com.lifetrack.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
@Schema(description = "反馈报告响应")
public class FeedbackReportResponse {
    @Schema(description = "报告标题", example = "本周成就总结：稳步前行的 Spring Boot 探索者")
    private String title;

    @Schema(description = "AI 总结文案", example = "虽然你本周在多个子任务中只前进了小步，但核心代码逻辑的完成为你下周的爆发奠定了基础。")
    private String aiSummary;

    @Schema(description = "成就标签列表", example = "[\"深度思考者\", \"代码稳健派\"]")
    private List<String> achievementTags;

    @Schema(description = "建议与展望", example = "下周可以尝试将精力集中在数据库优化模块。")
    private String suggestion;
}
