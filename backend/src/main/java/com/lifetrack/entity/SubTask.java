package com.lifetrack.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "sub_tasks")
@Schema(description = "子任务实体类")
public class SubTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "子任务ID", example = "1")
    private Long id;

    @Column(name = "task_id", nullable = false)
    @NotNull(message = "任务ID不能为空")
    @Schema(description = "所属主任务ID", example = "1")
    private Long taskId;

    @Column(nullable = false)
    @NotBlank(message = "子任务内容不能为空")
    @Schema(description = "子任务内容", example = "编写代码")
    private String content;

    @Column(nullable = false)
    @NotNull(message = "权重不能为空")
    @Schema(description = "子任务权重 (0.00-1.00)", example = "0.20")
    private BigDecimal weight;

    @Column(name = "current_progress")
    @Schema(description = "当前进度 (0.00-1.00)", example = "0.00")
    private BigDecimal currentProgress;

    @Column(name = "is_completed")
    @Schema(description = "是否完成 (0-否, 1-是)", example = "0")
    private Integer isCompleted; // 0-否, 1-是

    @Column(name = "order_num")
    @Schema(description = "排序序号", example = "1")
    private Integer orderNum;

    @PrePersist
    protected void onCreate() {
        if (currentProgress == null) {
            currentProgress = BigDecimal.ZERO;
        }
        if (isCompleted == null) {
            isCompleted = 0;
        }
        if (orderNum == null) {
            orderNum = 0;
        }
    }
}
