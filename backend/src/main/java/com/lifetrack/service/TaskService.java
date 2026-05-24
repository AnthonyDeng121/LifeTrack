package com.lifetrack.service;

import com.lifetrack.common.UserContext;
import com.lifetrack.dto.SubTaskListResponse;
import com.lifetrack.dto.TaskDeconstructRequest;
import com.lifetrack.dto.TaskDeconstructResponse;
import com.lifetrack.dto.TaskListResponse;
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

    /**
     * 获取当前用户所有进行中的任务列表
     */
    public List<TaskListResponse> getTaskList() {
        // 1. 从上下文获取当前登录用户 ID
        Long userId = UserContext.getUserId();

        // 2. 查询该用户下所有进行中的任务 (status = 0)
        List<Task> tasks = taskRepository.findByUserIdAndStatus(userId, 0);

        // 3. 转换为 DTO 并处理进度数值 (乘以 100)
        return tasks.stream().map(task -> TaskListResponse.builder()
                        .id(task.getId())
                        .title(task.getTitle())
                        .category(task.getCategory().name())
                        .totalProgress(task.getTotalProgress().multiply(new BigDecimal("100")))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 获取指定主任务下的所有子任务
     */
    public List<SubTaskListResponse> getSubTaskList(Long taskId) {
        // 1. 校验任务是否存在且属于当前用户
        Long userId = UserContext.getUserId();
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("任务不存在"));
        
        if (!task.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问该任务");
        }

        // 2. 查询子任务并按 orderNum 排序
        List<SubTask> subTasks = subTaskRepository.findByTaskIdOrderByOrderNumAsc(taskId);

        // 3. 转换为 DTO
        return subTasks.stream().map(st -> SubTaskListResponse.builder()
                        .id(st.getId())
                        .content(st.getContent())
                        .weight(st.getWeight().multiply(new BigDecimal("100")))
                        .currentProgress(st.getCurrentProgress().multiply(new BigDecimal("100")))
                        .isCompleted(st.getIsCompleted())
                        .build())
                .collect(Collectors.toList());
    }

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
