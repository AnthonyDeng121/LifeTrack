package com.lifetrack.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "行为同步响应")
public class ActionSyncResponse {
    @Schema(description = "匹配到的主任务名称", example = "学会 Spring Boot 开发")
    private String matchedTask;
    
    @Schema(description = "本次贡献增量", example = "5.0")
    private BigDecimal increment;
    
    @Schema(description = "更新后的总进度", example = "72.5")
    private BigDecimal newTotalProgress;
    
    @Schema(description = "AI 对该行为的分析", example = "干得漂亮！你已经掌握了 Web 开发的核心。")
    private String aiAnalysis;
}
