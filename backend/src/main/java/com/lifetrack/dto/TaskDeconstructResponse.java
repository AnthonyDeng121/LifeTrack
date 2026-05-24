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
@Schema(description = "任务拆解响应对象")
public class TaskDeconstructResponse {
    @Schema(description = "保存后的任务ID", example = "1")
    private Long taskId;
    
    @Schema(description = "拆解后的子任务列表")
    private List<SubTaskDTO> subTasks;
    
    @Schema(description = "AI 提供的建议")
    private String aiSuggestion;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "子任务传输对象")
    public static class SubTaskDTO {
        @Schema(description = "子任务内容", example = "环境搭建")
        private String content;
        
        @Schema(description = "子任务权重", example = "0.2")
        private BigDecimal weight;
    }
}
