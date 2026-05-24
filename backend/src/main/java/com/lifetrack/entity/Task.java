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
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tasks")
@Schema(description = "任务实体类")
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "任务ID", example = "1")
    private Long id;

    @Column(name = "user_id", nullable = false)
    @NotNull(message = "用户ID不能为空")
    @Schema(description = "用户ID", example = "1")
    private Long userId;

    @Column(nullable = false, length = 100)
    @NotBlank(message = "任务标题不能为空")
    @Schema(description = "任务标题", example = "完成项目报告")
    private String title;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "任务分类不能为空")
    @Schema(description = "任务分类")
    private Category category;

    @Schema(description = "任务状态 (0-进行中, 1-已完成)", example = "0")
    private Integer status; // 0-进行中, 1-已完成

    @Column(name = "total_progress")
    @Schema(description = "总进度 (0.00-1.00)", example = "0.50")
    private BigDecimal totalProgress;

    @Column(name = "ai_suggestion", columnDefinition = "TEXT")
    @Schema(description = "AI 建议内容")
    private String aiSuggestion;

    @Column(name = "created_at", updatable = false)
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "任务分类枚举")
    public enum Category {
        学习, 娱乐, 休息, 运动, 琐事
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (totalProgress == null) {
            totalProgress = BigDecimal.ZERO;
        }
        if (status == null) {
            status = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
