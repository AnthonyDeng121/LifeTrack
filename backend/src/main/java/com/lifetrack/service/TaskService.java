package com.lifetrack.service;

import com.lifetrack.common.UserContext;
import com.lifetrack.dto.SubTaskListResponse;
import com.lifetrack.dto.SubTaskUpdateRequest;
import com.lifetrack.dto.TaskDeconstructRequest;
import com.lifetrack.dto.TaskDeconstructResponse;
import com.lifetrack.dto.TaskListResponse;
import com.lifetrack.entity.SubTask;
import com.lifetrack.entity.Task;
import com.lifetrack.exception.BusinessException;
import com.lifetrack.repository.ActionLogRepository;
import com.lifetrack.repository.SubTaskRepository;
import com.lifetrack.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final SubTaskRepository subTaskRepository;
    private final ActionLogRepository actionLogRepository;
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
                .orElseThrow(() -> new BusinessException(404, "任务不存在"));
        
        if (!task.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问该任务");
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

        // 2. 创建并保存主任务
        Long userId = UserContext.getUserId();
        Task task = Task.builder()
                .userId(userId)
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

    /**
     * 删除子任务并按比例重新分配权重
     * @param subTaskId 子任务ID
     */
    @Transactional
    public void deleteSubTask(Long subTaskId) {
        // 1. 查找子任务
        SubTask targetSubTask = subTaskRepository.findById(subTaskId)
                .orElseThrow(() -> new BusinessException(404, "子任务不存在"));

        // 2. 权限校验
        Long userId = UserContext.getUserId();
        Task parentTask = taskRepository.findById(targetSubTask.getTaskId())
                .orElseThrow(() -> new BusinessException(404, "关联主任务不存在"));
        if (!parentTask.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该任务");
        }

        // 3. 获取同属于该主任务的其他子任务
        List<SubTask> remainingSubTasks = subTaskRepository.findByTaskIdOrderByOrderNumAsc(parentTask.getId())
                .stream()
                .filter(st -> !st.getId().equals(subTaskId))
                .collect(Collectors.toList());

        // 4. 重新分配权重
        if (!remainingSubTasks.isEmpty()) {
            // 计算剩余子任务的总权重
            BigDecimal remainingWeightSum = remainingSubTasks.stream()
                    .map(SubTask::getWeight)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (remainingWeightSum.compareTo(BigDecimal.ZERO) > 0) {
                // 按比例分配，确保总和为 1.0 (使用 1 / remainingWeightSum 作为系数)
                for (SubTask st : remainingSubTasks) {
                    BigDecimal newWeight = st.getWeight().divide(remainingWeightSum, 4, RoundingMode.HALF_UP);
                    st.setWeight(newWeight);
                }
                // 修正最后一个子任务的权重，防止精度丢失导致总和不为1
                BigDecimal totalNewWeight = remainingSubTasks.stream()
                        .map(SubTask::getWeight)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (totalNewWeight.compareTo(BigDecimal.ONE) != 0) {
                    SubTask last = remainingSubTasks.get(remainingSubTasks.size() - 1);
                    last.setWeight(last.getWeight().add(BigDecimal.ONE.subtract(totalNewWeight)));
                }
                subTaskRepository.saveAll(remainingSubTasks);
            }
        }

        // 5. 删除子任务
        subTaskRepository.delete(targetSubTask);

        // 6. 更新主任务总进度
        updateTaskProgress(parentTask.getId());
    }

    /**
     * 手动完成子任务
     * @param subTaskId 子任务ID
     */
    @Transactional
    public void completeSubTask(Long subTaskId) {
        // 1. 查找并校验权限
        SubTask subTask = subTaskRepository.findById(subTaskId)
                .orElseThrow(() -> new BusinessException(404, "子任务不存在"));
        
        Long userId = UserContext.getUserId();
        Task parentTask = taskRepository.findById(subTask.getTaskId())
                .orElseThrow(() -> new BusinessException(404, "关联主任务不存在"));
        if (!parentTask.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该任务");
        }

        // 2. 更新状态
        subTask.setIsCompleted(1);
        subTask.setCurrentProgress(BigDecimal.ONE);
        subTaskRepository.save(subTask);

        // 3. 更新主任务总进度
        updateTaskProgress(parentTask.getId());
    }

    /**
     * 更新子任务内容或权重
     * 如果更新了权重，其他子任务的权重将自动按比例缩放以保持总和为 1.0
     */
    @Transactional
    public void updateSubTask(Long subTaskId, SubTaskUpdateRequest request) {
        // 1. 查找并校验权限
        SubTask subTask = subTaskRepository.findById(subTaskId)
                .orElseThrow(() -> new BusinessException(404, "子任务不存在"));
        
        Long userId = UserContext.getUserId();
        Task parentTask = taskRepository.findById(subTask.getTaskId())
                .orElseThrow(() -> new BusinessException(404, "关联主任务不存在"));
        if (!parentTask.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该任务");
        }

        // 2. 更新内容
        if (request.getContent() != null) {
            subTask.setContent(request.getContent());
        }

        // 3. 更新权重并重新分配
        if (request.getWeight() != null) {
            BigDecimal newWeight = request.getWeight();
            
            // 获取其他子任务
            List<SubTask> otherSubTasks = subTaskRepository.findByTaskIdOrderByOrderNumAsc(parentTask.getId())
                    .stream()
                    .filter(st -> !st.getId().equals(subTaskId))
                    .collect(Collectors.toList());

            if (otherSubTasks.isEmpty()) {
                // 只有一个子任务时，权重必须为 1.0
                subTask.setWeight(BigDecimal.ONE);
            } else {
                // 计算其他子任务原有的总权重
                BigDecimal othersOldWeightSum = otherSubTasks.stream()
                        .map(SubTask::getWeight)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // 剩余需要分配的权重
                BigDecimal remainingToDistribute = BigDecimal.ONE.subtract(newWeight);

                if (othersOldWeightSum.compareTo(BigDecimal.ZERO) > 0) {
                    // 按比例分配剩余权重
                    for (SubTask ost : otherSubTasks) {
                        BigDecimal ostNewWeight = ost.getWeight()
                                .multiply(remainingToDistribute)
                                .divide(othersOldWeightSum, 4, RoundingMode.HALF_UP);
                        ost.setWeight(ostNewWeight);
                    }
                } else {
                    // 如果原本其他子任务权重都是0，则平均分配
                    BigDecimal average = remainingToDistribute.divide(new BigDecimal(otherSubTasks.size()), 4, RoundingMode.HALF_UP);
                    otherSubTasks.forEach(ost -> ost.setWeight(average));
                }

                // 修正精度，确保总和为 1.0
                BigDecimal totalWeight = otherSubTasks.stream()
                        .map(SubTask::getWeight)
                        .reduce(newWeight, BigDecimal::add);
                
                if (totalWeight.compareTo(BigDecimal.ONE) != 0) {
                    SubTask last = otherSubTasks.get(otherSubTasks.size() - 1);
                    last.setWeight(last.getWeight().add(BigDecimal.ONE.subtract(totalWeight)));
                }

                subTask.setWeight(newWeight);
                subTaskRepository.saveAll(otherSubTasks);
            }
        }

        subTaskRepository.save(subTask);

        // 4. 更新主任务进度 (因为权重变了，进度也会变)
        updateTaskProgress(parentTask.getId());
    }

    /**
     * 重新计算主任务的总进度
     * 公式: Σ(子任务权重 * 子任务当前进度)
     */
    public void updateTaskProgress(Long taskId) {
        List<SubTask> subTasks = subTaskRepository.findByTaskIdOrderByOrderNumAsc(taskId);
        BigDecimal totalProgress = subTasks.stream()
                .map(st -> st.getWeight().multiply(st.getCurrentProgress()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task != null) {
            task.setTotalProgress(totalProgress);
            // 如果进度达到 1.0，自动标记主任务为已完成 (可选逻辑，按需添加)
            if (totalProgress.compareTo(BigDecimal.ONE) >= 0) {
                task.setStatus(1);
            } else {
                task.setStatus(0);
            }
            taskRepository.save(task);
        }
    }

    /**
     * 删除任务及其关联的子任务和行为日志
     * @param taskId 任务ID
     */
    @Transactional
    public void deleteTask(Long taskId) {
        // 1. 校验任务是否存在且属于当前用户
        Long userId = UserContext.getUserId();
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "任务不存在"));

        if (!task.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权删除该任务");
        }

        // 2. 级联删除子任务
        subTaskRepository.deleteByTaskId(taskId);

        // 3. 级联删除关联的行为日志
        actionLogRepository.deleteByTaskId(taskId);

        // 4. 删除主任务
        taskRepository.delete(task);
    }
}
