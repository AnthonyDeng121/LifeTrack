package com.lifetrack.service;

import com.lifetrack.common.UserContext;
import com.lifetrack.dto.SubTaskListResponse;
import com.lifetrack.dto.SubTaskUpdateRequest;
import com.lifetrack.dto.TaskDeconstructRequest;
import com.lifetrack.dto.TaskDeconstructResponse;
import com.lifetrack.dto.TaskListResponse;
import com.lifetrack.dto.TaskDetailResponse;
import com.lifetrack.dto.TaskUpdateRequest;
import com.lifetrack.entity.SubTask;
import com.lifetrack.entity.Task;
import com.lifetrack.entity.PlanStatus;
import com.lifetrack.exception.BusinessException;
import com.lifetrack.repository.ActionLogRepository;
import com.lifetrack.repository.SubTaskRepository;
import com.lifetrack.repository.TaskRepository;
import com.lifetrack.repository.CheckInRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class TaskService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final TaskRepository taskRepository;
    private final SubTaskRepository subTaskRepository;
    private final ActionLogRepository actionLogRepository;
    private final AIService aiService;
    private final CheckInRecordRepository checkInRecordRepository;

    /**
     * 手动创建任务
     */
    @Transactional
    public TaskDeconstructResponse createManualTask(com.lifetrack.dto.ManualTaskCreateRequest request) {
        Long userId = UserContext.getUserId();
        validatePlanPeriod(request.getStartDate(), request.getEndDate(), request.getTargetDays());
        
        // 1. 创建主任务
        Task task = Task.builder()
                .userId(userId)
                .title(request.getTitle())
                .category(Task.Category.valueOf(request.getCategory()))
                .status(initialStatus(request.getStartDate()))
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .targetDays(request.getTargetDays())
                .totalProgress(BigDecimal.ZERO)
                .aiSuggestion("手动创建的任务，请按计划执行。")
                .build();
        Task savedTask = taskRepository.save(task);

        // 2. 准备子任务
        List<SubTask> subTaskList = new ArrayList<>();
        List<com.lifetrack.dto.ManualTaskCreateRequest.SubTaskDTO> dtoList = request.getSubTasks();
        
        // 如果用户没填权重，平均分配
        BigDecimal defaultWeight = BigDecimal.ONE.divide(new BigDecimal(dtoList.size()), 4, RoundingMode.HALF_UP);

        for (int i = 0; i < dtoList.size(); i++) {
            com.lifetrack.dto.ManualTaskCreateRequest.SubTaskDTO dto = dtoList.get(i);
            subTaskList.add(SubTask.builder()
                    .taskId(savedTask.getId())
                    .content(dto.getContent())
                    .weight(dto.getWeight() != null ? dto.getWeight() : defaultWeight)
                    .currentProgress(BigDecimal.ZERO)
                    .isCompleted(0)
                    .orderNum(i)
                    .build());
        }

        // 校验总权重是否为 1.0，如果不是，修正最后一个
        BigDecimal totalWeight = subTaskList.stream()
                .map(SubTask::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalWeight.compareTo(BigDecimal.ONE) != 0) {
            SubTask last = subTaskList.get(subTaskList.size() - 1);
            last.setWeight(last.getWeight().add(BigDecimal.ONE.subtract(totalWeight)));
        }

        subTaskRepository.saveAll(subTaskList);

        return TaskDeconstructResponse.builder()
                .taskId(savedTask.getId())
                .subTasks(subTaskList.stream()
                        .map(st -> new TaskDeconstructResponse.SubTaskDTO(st.getContent(), st.getWeight()))
                        .collect(Collectors.toList()))
                .aiSuggestion(task.getAiSuggestion())
                .build();
    }

    /**
     * 获取当前用户所有进行中的任务列表
     */
    @Transactional
    public List<TaskListResponse> getTaskList(String statusValue, LocalDate startDate, LocalDate endDate) {
        // 1. 从上下文获取当前登录用户 ID
        Long userId = UserContext.getUserId();

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(400, "筛选开始日期不能晚于结束日期");
        }
        PlanStatus requestedStatus = PlanStatus.fromValue(statusValue);
        List<Task> tasks = taskRepository.findByUserId(userId);
        tasks.forEach(this::normalizeLegacyPlan);
        taskRepository.saveAll(tasks);

        // 3. 转换为 DTO 并处理进度数值 (乘以 100)
        return tasks.stream()
                .filter(task -> requestedStatus == null || task.getStatus() == requestedStatus)
                .filter(task -> startDate == null || !task.getEndDate().isBefore(startDate))
                .filter(task -> endDate == null || !task.getStartDate().isAfter(endDate))
                .map(task -> TaskListResponse.builder()
                        .id(task.getId())
                        .title(task.getTitle())
                        .category(task.getCategory().name())
                        .totalProgress(task.getTotalProgress().multiply(new BigDecimal("100")))
                        .startDate(task.getStartDate())
                        .endDate(task.getEndDate())
                        .targetDays(task.getTargetDays())
                        .status(task.getStatus().name())
                        .statusDescription(task.getStatus().getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public TaskDetailResponse getTaskDetail(Long taskId) {
        Task task = getOwnedTask(taskId);
        normalizeLegacyPlan(task);
        taskRepository.save(task);
        return toDetailResponse(task);
    }

    @Transactional
    public TaskDetailResponse updateTask(Long taskId, TaskUpdateRequest request) {
        Task task = getOwnedTask(taskId);
        normalizeLegacyPlan(task);

        if (request.getTitle() != null) {
            if (request.getTitle().isBlank()) {
                throw new BusinessException(400, "计划标题不能为空");
            }
            task.setTitle(request.getTitle().trim());
        }
        if (request.getCategory() != null) {
            try {
                task.setCategory(Task.Category.valueOf(request.getCategory()));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(400, "非法的计划分类: " + request.getCategory());
            }
        }

        LocalDate nextStart = request.getStartDate() != null ? request.getStartDate() : task.getStartDate();
        LocalDate nextEnd = request.getEndDate() != null ? request.getEndDate() : task.getEndDate();
        Integer nextTargetDays = request.getTargetDays() != null ? request.getTargetDays() : task.getTargetDays();
        validatePlanPeriod(nextStart, nextEnd, nextTargetDays);
        task.setStartDate(nextStart);
        task.setEndDate(nextEnd);
        task.setTargetDays(nextTargetDays);

        PlanStatus targetStatus = PlanStatus.fromValue(request.getStatus());
        if (targetStatus != null && !task.getStatus().canTransitionTo(targetStatus)) {
            throw new BusinessException(400, "计划状态不能从" + task.getStatus().getDescription()
                    + "变更为" + targetStatus.getDescription());
        }
        if (targetStatus != null) {
            task.setStatus(targetStatus);
        } else {
            refreshStatusByDate(task);
        }

        return toDetailResponse(taskRepository.save(task));
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
        validatePlanPeriod(request.getStartDate(), request.getEndDate(), request.getTargetDays());
        // 1. 调用 AI 服务获取拆解结果
        TaskDeconstructResponse.SubTaskDTO[] subTaskDTOs = aiService.deconstructTask(request.getTitle());
        
        // 获取当前用户进度并生成激励语
        String username = UserContext.getUsername() != null ? UserContext.getUsername() : "同学";
        String aiSuggestion = aiService.generateSuggestion(0, 0, username);

        // 2. 创建并保存主任务
        Long userId = UserContext.getUserId();
        Task task = Task.builder()
                .userId(userId)
                .title(request.getTitle())
                .category(Task.Category.valueOf(request.getCategory()))
                .status(initialStatus(request.getStartDate()))
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .targetDays(request.getTargetDays())
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
                task.setStatus(PlanStatus.COMPLETED);
            } else {
                refreshStatusByDate(task);
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

        // 4. 删除正式打卡记录
        checkInRecordRepository.deleteByTaskId(taskId);

        // 5. 删除主任务
        taskRepository.delete(task);
    }

    @Transactional
    public void deleteTasks(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty() || taskIds.size() > 100
                || taskIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(400, "请选择1到100个有效任务");
        }
        List<Long> ids = taskIds.stream().distinct().toList();
        // 全部校验通过后才删除，任一越权或不存在均不删除任何任务。
        ids.forEach(this::getOwnedTask);
        ids.forEach(this::deleteTask);
    }

    private Task getOwnedTask(Long taskId) {
        Long userId = UserContext.getUserId();
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "学习计划不存在"));
        if (!task.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问该学习计划");
        }
        return task;
    }

    private void validatePlanPeriod(LocalDate startDate, LocalDate endDate, Integer targetDays) {
        if (startDate == null || endDate == null) {
            throw new BusinessException(400, "计划开始日期和结束日期不能为空");
        }
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(400, "计划开始日期不能晚于结束日期");
        }
        long periodDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (targetDays == null || targetDays < 1 || targetDays > periodDays) {
            throw new BusinessException(400, "目标打卡天数必须在1到计划周期天数之间");
        }
    }

    private PlanStatus initialStatus(LocalDate startDate) {
        return startDate.isAfter(today()) ? PlanStatus.NOT_STARTED : PlanStatus.IN_PROGRESS;
    }

    private void normalizeLegacyPlan(Task task) {
        LocalDate fallbackStart = task.getCreatedAt() == null
                ? today()
                : task.getCreatedAt().toLocalDate();
        if (task.getStartDate() == null) task.setStartDate(fallbackStart);
        if (task.getEndDate() == null) task.setEndDate(task.getStartDate().plusDays(29));
        if (task.getTargetDays() == null) task.setTargetDays(30);
        if (task.getStatus() == null) task.setStatus(PlanStatus.IN_PROGRESS);
        refreshStatusByDate(task);
    }

    private void refreshStatusByDate(Task task) {
        if (task.getStatus() == PlanStatus.COMPLETED || task.getStatus() == PlanStatus.CANCELLED) return;
        LocalDate businessToday = today();
        if (task.getEndDate().isBefore(businessToday)) {
            task.setStatus(PlanStatus.EXPIRED);
        } else if (task.getStartDate().isAfter(businessToday)) {
            task.setStatus(PlanStatus.NOT_STARTED);
        } else {
            task.setStatus(PlanStatus.IN_PROGRESS);
        }
    }

    private LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    private TaskDetailResponse toDetailResponse(Task task) {
        return TaskDetailResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .category(task.getCategory().name())
                .startDate(task.getStartDate())
                .endDate(task.getEndDate())
                .targetDays(task.getTargetDays())
                .status(task.getStatus().name())
                .statusDescription(task.getStatus().getDescription())
                .totalProgress(task.getTotalProgress().multiply(new BigDecimal("100")))
                .aiSuggestion(task.getAiSuggestion())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
