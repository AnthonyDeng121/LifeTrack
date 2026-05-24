package com.lifetrack.service;

import com.lifetrack.dto.TaskDeconstructResponse;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Service
public class AIService {

    /**
     * 模拟调用大模型进行任务拆解
     * 实际开发中，这里会由 李想/魏欣岳 提供 Prompt，你负责调用 DeepSeek/GPT 接口
     */
    public TaskDeconstructResponse.SubTaskDTO[] deconstructTask(String title) {
        // 模拟 AI 逻辑：根据输入返回不同的拆解结果
        if (title.contains("Spring Boot")) {
            return new TaskDeconstructResponse.SubTaskDTO[]{
                new TaskDeconstructResponse.SubTaskDTO("环境搭建与 Maven 使用", new BigDecimal("0.15")),
                new TaskDeconstructResponse.SubTaskDTO("Controller 与 RESTful API", new BigDecimal("0.25")),
                new TaskDeconstructResponse.SubTaskDTO("MyBatis 数据库操作", new BigDecimal("0.30")),
                new TaskDeconstructResponse.SubTaskDTO("Service 业务逻辑编写", new BigDecimal("0.20")),
                new TaskDeconstructResponse.SubTaskDTO("项目打包与部署", new BigDecimal("0.10"))
            };
        }
        
        // 默认兜底拆解
        return new TaskDeconstructResponse.SubTaskDTO[]{
            new TaskDeconstructResponse.SubTaskDTO("前期准备与资料搜集", new BigDecimal("0.20")),
            new TaskDeconstructResponse.SubTaskDTO("核心环节第一阶段实现", new BigDecimal("0.30")),
            new TaskDeconstructResponse.SubTaskDTO("核心环节第二阶段实现", new BigDecimal("0.30")),
            new TaskDeconstructResponse.SubTaskDTO("后期测试与总结汇报", new BigDecimal("0.20"))
        };
    }

    public String generateSuggestion(String title) {
        return "这是一个非常棒的目标！建议分解为小步骤，每天稳步推进。";
    }
}
