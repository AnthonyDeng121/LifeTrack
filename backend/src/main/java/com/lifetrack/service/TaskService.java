package com.lifetrack.service;

import com.lifetrack.dto.TaskDeconstructRequest;
import com.lifetrack.dto.TaskDeconstructResponse;
import com.lifetrack.entity.SubTask;
import com.lifetrack.entity.Task;
import com.lifetrack.repository.SubTaskRepository;
import com.lifetrack.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final SubTaskRepository subTaskRepository;
    private final AIService aiService;

    @Transactional
    public TaskDeconstructResponse deconstructTask(TaskDeconstructRequest request) {
        // 1. 调用 AI 服务获取拆解结果
        TaskDeconstructResponse.SubTaskDTO[] subTaskDTOs = aiService.deconstructTask(request.getTitle());
        String aiSuggestion = aiService.generateSuggestion(request.getTitle());

        // 2. 创建并保存主任务（此处暂硬编码 userId 为 1，后续对接 Auth）
        Task task = Task.builder()
                .userId(1L)
                .title(request.getTitle())
                .category(Task.Category.valueOf(request.getCategory()))
                .status(0)
                .totalProgress(BigDecimal.ZERO)
                .aiSuggestion(aiSuggestion)
                .build();
        Task savedTask = taskRepository.save(task);

        // 3. 批量保存子任务
        List<SubTask> subTaskList = new ArrayList<>();
        for (int i = 0; i < subTaskDTOs.length; i++) {
            TaskDeconstructResponse.SubTaskDTO dto = subTaskDTOs[i];
            subTaskList.add(SubTask.builder()
                    .taskId(savedTask.getId())
                    .content(dto.getContent())
                    .weight(dto.getWeight())
                    .currentProgress(BigDecimal.ZERO)
                    .isCompleted(0)
                    .orderNum(i)
                    .build());
        }
        subTaskRepository.saveAll(subTaskList);

        // 4. 封装返回结果
        List<TaskDeconstructResponse.SubTaskDTO> responseDTOs = subTaskList.stream()
                .map(st -> new TaskDeconstructResponse.SubTaskDTO(st.getContent(), st.getWeight()))
                .collect(Collectors.toList());

        return TaskDeconstructResponse.builder()
                .taskId(savedTask.getId())
                .subTasks(responseDTOs)
                .aiSuggestion(aiSuggestion)
                .build();
    }
}
