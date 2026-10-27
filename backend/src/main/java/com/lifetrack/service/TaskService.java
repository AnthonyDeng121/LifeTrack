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

    private final org.springframework.transaction.support.TransactionOperations recordTransactions;
    private final TaskRepository taskRepository;
    private final SubTaskRepository subTaskRepository;
    private final ActionLogRepository actionLogRepository;
    private final AIService aiService;
    private final CheckInRecordRepository checkInRecordRepository;
    private final com.lifetrack.repository.DeferredLearningRecordRepository deferredRecords;

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void addSubTask(Long taskId, com.lifetrack.dto.SubTaskCreateRequest request) {
        Task task = taskRepository.findLockedById(taskId)
                .orElseThrow(() -> new BusinessException(404, "计划不存在"));
        if (!task.getUserId().equals(UserContext.getUserId())) throw new BusinessException(403, "无权操作该计划");
        requireEditableSteps(task);
        if (request.getContent() == null || request.getContent().isBlank() || request.getContent().trim().length() > 255) {
            throw new BusinessException(400, "子计划内容应为1到255字");
        }
        BigDecimal weight = request.getWeight();
        if (weight == null || weight.compareTo(new BigDecimal("0.01")) < 0 || weight.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(400, "子计划权重应为1%到100%");
        }
        List<SubTask> existing = subTaskRepository.findByTaskIdOrderByOrderNumAsc(taskId);
        if (existing.size() >= 100) throw new BusinessException(400, "每个计划最多保留100个子计划");
        weight = existing.isEmpty() ? BigDecimal.ONE : weight.setScale(4, RoundingMode.HALF_UP);
        redistributeWeights(existing, BigDecimal.ONE.subtract(weight));
        subTaskRepository.saveAll(existing);
        int order = existing.stream().map(SubTask::getOrderNum).filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(0) + 1;
        subTaskRepository.save(SubTask.builder().taskId(taskId).content(request.getContent().trim())
                .weight(weight).currentProgress(BigDecimal.ZERO).isCompleted(0).orderNum(order).build());
        updateTaskProgress(taskId);
    }

    private void redistributeWeights(List<SubTask> steps, BigDecimal total) {
        if (steps.isEmpty()) return;
        BigDecimal original = steps.stream().map(SubTask::getWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal assigned = BigDecimal.ZERO;
        List<BigDecimal> weights = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            BigDecimal next = i == steps.size() - 1 ? total.subtract(assigned)
                    : (original.signum() > 0 ? steps.get(i).getWeight().multiply(total).divide(original, 4, RoundingMode.DOWN)
                    : total.divide(BigDecimal.valueOf(steps.size()), 4, RoundingMode.DOWN));
            if (next.compareTo(new BigDecimal("0.01")) < 0 || next.compareTo(BigDecimal.ONE) > 0)
                throw new BusinessException(400, "调整后每个子计划的权重都必须至少为1%，请降低所选权重或减少步骤");
            weights.add(next);
            assigned = assigned.add(next);
        }
        // 全部检查通过后才修改实体，避免部分更新。
        for (int i = 0; i < steps.size(); i++) steps.get(i).setWeight(weights.get(i));
    }

    /**
     * 手动创建任务
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public TaskDeconstructResponse createManualTask(com.lifetrack.dto.ManualTaskCreateRequest request) {
        Long userId = UserContext.getUserId();
        validateTitle(request.getTitle());
        validatePlanPeriod(request.getStartDate(), request.getEndDate(), request.getTargetDays());
        List<SubTask> subTaskList = prepareManualSteps(request.getSubTasks());
        
        // 1. 创建主任务
        Task task = Task.builder()
                .userId(userId)
                .title(request.getTitle().trim())
                .category(parseCategory(request.getCategory()))
                .status(statusByDate(request.getStartDate(), request.getEndDate()))
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .targetDays(request.getTargetDays())
                .totalProgress(BigDecimal.ZERO)
                .aiSuggestion("手动创建的任务，请按计划执行。")
                .build();
        Task savedTask = taskRepository.save(task);

        subTaskList.forEach(step -> step.setTaskId(savedTask.getId()));
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
    @Transactional(readOnly = true)
    public List<TaskListResponse> getTaskList(String statusValue, LocalDate startDate, LocalDate endDate) {
        // 1. 从上下文获取当前登录用户 ID
        Long userId = UserContext.getUserId();

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(400, "筛选开始日期不能晚于结束日期");
        }
        PlanStatus requestedStatus = PlanStatus.fromValue(statusValue);
        List<Task> tasks = taskRepository.findFiltered(userId, requestedStatus, startDate, endDate,
                today(), PlanStatus.IN_PROGRESS, PlanStatus.NOT_STARTED, PlanStatus.EXPIRED);
        tasks.forEach(this::normalizeLegacyPlan);
        java.util.Map<Long, Long> counts = tasks.isEmpty() ? java.util.Map.of() : subTaskRepository
                .countByPlans(tasks.stream().map(Task::getId).toList()).stream()
                .collect(Collectors.toMap(SubTaskRepository.PlanCount::getTaskId, SubTaskRepository.PlanCount::getStepCount));

        // 3. 转换为 DTO 并处理进度数值 (乘以 100)
        return tasks.stream()
                .filter(task -> requestedStatus == null || task.getStatus() == requestedStatus)
                .filter(task -> startDate == null || !task.getEndDate().isBefore(startDate))
                .filter(task -> endDate == null || !task.getStartDate().isAfter(endDate))
                .map(task -> TaskListResponse.builder()
                        .id(task.getId())
                        .subtaskCount(Math.toIntExact(counts.getOrDefault(task.getId(), 0L)))
                        .updatedAt(task.getUpdatedAt())
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

    @Transactional(readOnly = true)
    public TaskDetailResponse getTaskDetail(Long taskId) {
        Task task = getOwnedTask(taskId);
        normalizeLegacyPlan(task);
        return toDetailResponse(task);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public TaskDetailResponse updateTask(Long taskId, TaskUpdateRequest request) {
        Task task = taskRepository.findLockedById(taskId)
                .orElseThrow(() -> new BusinessException(404, "学习计划不存在"));
        if (!task.getUserId().equals(UserContext.getUserId())) throw new BusinessException(403, "无权访问该学习计划");
        if (request.getVersion() == null)
            throw new BusinessException(400, "缺少计划版本，请刷新后重试");
        if (!java.util.Objects.equals(request.getVersion(), task.getVersion()))
            throw new BusinessException(409, "计划已被其他操作修改，请刷新后重新编辑");
        normalizeLegacyPlan(task);

        if (request.getTitle() != null) {
            validateTitle(request.getTitle());
            task.setTitle(request.getTitle().trim());
        }
        if (request.getCategory() != null) {
            task.setCategory(parseCategory(request.getCategory()));
        }

        LocalDate nextStart = request.getStartDate() != null ? request.getStartDate() : task.getStartDate();
        LocalDate nextEnd = request.getEndDate() != null ? request.getEndDate() : task.getEndDate();
        Integer nextTargetDays = request.getTargetDays() != null ? request.getTargetDays() : task.getTargetDays();
        validatePlanPeriod(nextStart, nextEnd, nextTargetDays);
        PlanStatus targetStatus = PlanStatus.fromValue(request.getStatus());
        boolean periodChanged = !nextStart.equals(task.getStartDate()) || !nextEnd.equals(task.getEndDate());
        boolean closed = task.getStatus() == PlanStatus.COMPLETED || task.getStatus() == PlanStatus.CANCELLED;
        if (closed && (periodChanged || !nextTargetDays.equals(task.getTargetDays()))) {
            throw new BusinessException(400, "已完成或已取消计划不可修改周期和打卡目标，请新建计划");
        }
        PlanStatus datedStatus = statusByDate(nextStart, nextEnd);
        if (task.getStatus() == PlanStatus.EXPIRED && datedStatus != PlanStatus.EXPIRED) {
            throw new BusinessException(400, "已过期计划不可重新开始，请新建计划");
        }
        // 日期状态随周期派生；完成和取消为业务终态，不能通过改日期或显式状态绕过。
        if (targetStatus != null && (closed || targetStatus == PlanStatus.COMPLETED || targetStatus == PlanStatus.CANCELLED)
                && !task.getStatus().canTransitionTo(targetStatus)) {
            throw new BusinessException(400, "计划状态不能从" + task.getStatus().getDescription()
                    + "变更为" + targetStatus.getDescription());
        }
        if (!closed && targetStatus != null && targetStatus != PlanStatus.COMPLETED
                && targetStatus != PlanStatus.CANCELLED && targetStatus != datedStatus) {
            throw new BusinessException(400, "计划状态与周期不一致，当前周期对应" + datedStatus.getDescription());
        }
        if (targetStatus == PlanStatus.COMPLETED && !closed && datedStatus != PlanStatus.IN_PROGRESS) {
            throw new BusinessException(400, "只有有效周期内的进行中计划可以完成");
        }
        if (periodChanged) {
            List<com.lifetrack.entity.CheckInRecord> outside = checkInRecordRepository
                    .findByUserIdAndTaskIdOrderByCheckInDateAsc(task.getUserId(), taskId).stream()
                    .filter(record -> record.getCheckInDate().isBefore(nextStart) || record.getCheckInDate().isAfter(nextEnd))
                    .toList();
            java.util.Set<Long> ids = outside.stream().map(com.lifetrack.entity.CheckInRecord::getId)
                    .collect(Collectors.toSet());
            java.util.Set<Long> confirmed = request.getConfirmedDiscardCheckInIds() == null ? java.util.Set.of()
                    : new java.util.HashSet<>(request.getConfirmedDiscardCheckInIds());
            if (!outside.isEmpty() && (!Boolean.TRUE.equals(request.getDiscardOutsideCheckIns()) || !ids.equals(confirmed))) {
                throw new BusinessException(409, "新周期外有" + outside.size() + "条打卡记录，请重新预览并确认清零后修改");
            }
            checkInRecordRepository.deleteAll(outside);
        }
        task.setStartDate(nextStart);
        task.setEndDate(nextEnd);
        task.setTargetDays(nextTargetDays);
        task.setStatus(closed ? task.getStatus() : targetStatus == PlanStatus.COMPLETED || targetStatus == PlanStatus.CANCELLED
                ? targetStatus : datedStatus);

        Task saved = taskRepository.save(task);
        taskRepository.flush(); // 让响应携带这次写入后的版本，而不是刷新前的旧版本。
        return toDetailResponse(saved);
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

    public TaskDeconstructResponse previewTask(com.lifetrack.dto.TaskPreviewRequest request) {
        return TaskDeconstructResponse.builder()
                .subTasks(java.util.Arrays.asList(aiService.previewTask(request)))
                .aiSuggestion("最后一步为目标验收；确认步骤符合预期后再保存。")
                .build();
    }

    public TaskDeconstructResponse deconstructTask(TaskDeconstructRequest request) {
        validateTitle(request.getTitle());
        validatePlanPeriod(request.getStartDate(), request.getEndDate(), request.getTargetDays());
        // 1. 调用 AI 服务获取拆解结果
        TaskDeconstructResponse.SubTaskDTO[] subTaskDTOs = aiService.deconstructTask(request.getTitle());
        List<com.lifetrack.dto.ManualTaskCreateRequest.SubTaskDTO> input = java.util.Arrays.stream(subTaskDTOs)
                .map(dto -> {
                    var step = new com.lifetrack.dto.ManualTaskCreateRequest.SubTaskDTO();
                    step.setContent(dto.getContent());
                    step.setWeight(dto.getWeight());
                    return step;
                }).toList();
        List<SubTask> subTaskList = prepareManualSteps(input);
        
        // 获取当前用户进度并生成激励语
        String username = UserContext.getUsername() != null ? UserContext.getUsername() : "同学";
        String aiSuggestion = aiService.generateSuggestion(0, 0, username);

        return recordTransactions.execute(tx -> {
        // 2. 创建并保存主任务
        Long userId = UserContext.getUserId();
        Task task = Task.builder()
                .userId(userId)
                .title(request.getTitle().trim())
                .category(parseCategory(request.getCategory()))
                .status(statusByDate(request.getStartDate(), request.getEndDate()))
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .targetDays(request.getTargetDays())
                .totalProgress(BigDecimal.ZERO)
                .aiSuggestion(aiSuggestion)
                .build();
        Task savedTask = taskRepository.save(task);

        // 3. 批量保存子任务
        subTaskList.forEach(step -> step.setTaskId(savedTask.getId()));
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
        });
    }

    /**
     * 删除子任务并按比例重新分配权重
     * @param subTaskId 子任务ID
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void deleteSubTask(Long subTaskId) {
        // 1. 查找子任务
        Long parentId = subTaskRepository.findTaskIdById(subTaskId)
                .orElseThrow(() -> new BusinessException(404, "子任务不存在"));

        // 2. 权限校验
        Long userId = UserContext.getUserId();
        Task parentTask = taskRepository.findLockedById(parentId)
                .orElseThrow(() -> new BusinessException(404, "关联主任务不存在"));
        if (!parentTask.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该任务");
        }
        requireEditableSteps(parentTask);
        SubTask targetSubTask = subTaskRepository.findById(subTaskId)
                .orElseThrow(() -> new BusinessException(404, "子任务已删除"));

        // 3. 获取同属于该主任务的其他子任务
        List<SubTask> remainingSubTasks = subTaskRepository.findByTaskIdOrderByOrderNumAsc(parentTask.getId())
                .stream()
                .filter(st -> !st.getId().equals(subTaskId))
                .collect(Collectors.toList());

        if (remainingSubTasks.isEmpty()) throw new BusinessException(400, "至少保留一个子计划，请先添加替代步骤");
        redistributeWeights(remainingSubTasks, BigDecimal.ONE);
        subTaskRepository.saveAll(remainingSubTasks);

        // 5. 删除子任务
        subTaskRepository.delete(targetSubTask);

        // 6. 更新主任务总进度
        updateTaskProgress(parentTask.getId());
    }

    /**
     * 手动完成子任务
     * @param subTaskId 子任务ID
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void completeSubTask(Long subTaskId) {
        // 1. 查找并校验权限
        Long parentId = subTaskRepository.findTaskIdById(subTaskId)
                .orElseThrow(() -> new BusinessException(404, "子任务不存在"));
        
        Long userId = UserContext.getUserId();
        Task parentTask = taskRepository.findLockedById(parentId)
                .orElseThrow(() -> new BusinessException(404, "关联主任务不存在"));
        if (!parentTask.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该任务");
        }
        requireEditableSteps(parentTask);
        SubTask subTask = subTaskRepository.findById(subTaskId)
                .orElseThrow(() -> new BusinessException(404, "子任务已删除"));
        if (parentTask.getStatus() != PlanStatus.IN_PROGRESS) {
            throw new BusinessException(400, "只有进行中的计划可以完成子计划");
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
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void updateSubTask(Long subTaskId, SubTaskUpdateRequest request) {
        // 1. 查找并校验权限
        Long parentId = subTaskRepository.findTaskIdById(subTaskId)
                .orElseThrow(() -> new BusinessException(404, "子任务不存在"));
        
        Long userId = UserContext.getUserId();
        Task parentTask = taskRepository.findLockedById(parentId)
                .orElseThrow(() -> new BusinessException(404, "关联主任务不存在"));
        if (!parentTask.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该任务");
        }
        requireEditableSteps(parentTask);
        SubTask subTask = subTaskRepository.findById(subTaskId)
                .orElseThrow(() -> new BusinessException(404, "子任务已删除"));

        // 2. 更新内容
        if (request.getContent() != null) {
            if (request.getContent().isBlank() || request.getContent().trim().length() > 255) {
                throw new BusinessException(400, "子计划内容应为1到255字");
            }
            subTask.setContent(request.getContent().trim());
        }

        if (request.getWeight() != null) {
            BigDecimal weight = request.getWeight();
            if (weight.compareTo(new BigDecimal("0.01")) < 0 || weight.compareTo(BigDecimal.ONE) > 0) {
                throw new BusinessException(400, "子计划权重应为1%到100%");
            }
            List<SubTask> others = subTaskRepository.findByTaskIdOrderByOrderNumAsc(parentTask.getId()).stream()
                    .filter(st -> !st.getId().equals(subTaskId)).toList();
            weight = others.isEmpty() ? BigDecimal.ONE : weight.setScale(4, RoundingMode.HALF_UP);
            redistributeWeights(others, BigDecimal.ONE.subtract(weight));
            subTask.setWeight(weight);
            subTaskRepository.saveAll(others);
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
            // 完成计划为冻结快照，所有进度入口都不得隐式重新打开。
            if (task.getStatus() == PlanStatus.COMPLETED || task.getStatus() == PlanStatus.CANCELLED) return;
            refreshStatusByDate(task);
            task.setTotalProgress(totalProgress);
            // 如果进度达到 1.0，自动标记主任务为已完成 (可选逻辑，按需添加)
            if (task.getStatus() == PlanStatus.IN_PROGRESS && totalProgress.compareTo(BigDecimal.ONE) >= 0) {
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
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void deleteTask(Long taskId) {
        // 1. 校验任务是否存在且属于当前用户
        Long userId = UserContext.getUserId();
        Task task = taskRepository.findLockedById(taskId)
                .orElseThrow(() -> new BusinessException(404, "任务不存在"));

        if (!task.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权删除该任务");
        }

        // 2. 级联删除子任务
        actionLogRepository.deleteByTaskId(taskId);
        subTaskRepository.deleteByTaskId(taskId);

        // 4. 删除正式打卡记录
        checkInRecordRepository.deleteByTaskId(taskId);
        deferredRecords.deleteByTaskId(taskId);

        // 5. 删除主任务
        taskRepository.delete(task);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void deleteTasks(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty() || taskIds.size() > 100
                || taskIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(400, "请选择1到100个有效任务");
        }
        List<Long> ids = taskIds.stream().distinct().sorted().toList();
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

    private void validateTitle(String title) {
        if (title == null || title.isBlank()) throw new BusinessException(400, "计划标题不能为空");
        if (title.length() > 100) throw new BusinessException(400, "计划标题不能超过100个字符");
    }

    private PlanStatus statusByDate(LocalDate startDate, LocalDate endDate) {
        LocalDate businessToday = today();
        if (endDate.isBefore(businessToday)) return PlanStatus.EXPIRED;
        return startDate.isAfter(businessToday) ? PlanStatus.NOT_STARTED : PlanStatus.IN_PROGRESS;
    }

    private Task.Category parseCategory(String value) {
        try {
            if (value != null && !value.isBlank()) return Task.Category.valueOf(value);
        } catch (IllegalArgumentException ignored) { }
        throw new BusinessException(400, "请选择有效的计划分类");
    }

    private void requireEditableSteps(Task task) {
        normalizeLegacyPlan(task);
        if (task.getStatus() != PlanStatus.IN_PROGRESS && task.getStatus() != PlanStatus.NOT_STARTED) {
            throw new BusinessException(400, task.getStatus().getDescription() + "计划不可修改子计划或学习进度，请新建计划");
        }
    }

    private List<SubTask> prepareManualSteps(List<com.lifetrack.dto.ManualTaskCreateRequest.SubTaskDTO> input) {
        if (input == null || input.isEmpty() || input.size() > 100) {
            throw new BusinessException(400, "每个计划需要1到100个子计划");
        }
        BigDecimal specified = BigDecimal.ZERO;
        int missing = 0;
        for (var dto : input) {
            if (dto == null || dto.getContent() == null || dto.getContent().isBlank() || dto.getContent().trim().length() > 255) {
                throw new BusinessException(400, "子计划内容应为1到255字");
            }
            BigDecimal weight = dto.getWeight();
            if (weight == null) { missing++; continue; }
            if (weight.signum() <= 0 || weight.compareTo(BigDecimal.ONE) > 0) {
                throw new BusinessException(400, "子计划权重必须大于0且不超过100%");
            }
            specified = specified.add(weight);
        }
        BigDecimal remainder = BigDecimal.ONE.subtract(specified);
        BigDecimal tolerance = new BigDecimal("0.0001").multiply(BigDecimal.valueOf(input.size()));
        if (missing > 0 ? remainder.signum() <= 0 : remainder.abs().compareTo(tolerance) > 0) {
            throw new BusinessException(400, "子计划权重总和必须为100%，未填写项将分配剩余权重");
        }
        BigDecimal defaultWeight = missing == 0 ? BigDecimal.ZERO : remainder.divide(BigDecimal.valueOf(missing), 8, RoundingMode.HALF_UP);
        List<SubTask> result = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            var dto = input.get(i);
            result.add(SubTask.builder().content(dto.getContent().trim())
                    .weight(dto.getWeight() == null ? defaultWeight : dto.getWeight())
                    .currentProgress(BigDecimal.ZERO).isCompleted(0).orderNum(i).build());
        }
        // 比例归一化消除有限精度误差；不再把任意差额压给最后一项。
        redistributeWeights(result, BigDecimal.ONE);
        if (result.stream().anyMatch(step -> step.getWeight().signum() <= 0 || step.getWeight().compareTo(BigDecimal.ONE) > 0)) {
            throw new BusinessException(400, "子计划权重过小，请调整后重试");
        }
        return result;
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
        if (task.getStatus() == PlanStatus.COMPLETED || task.getStatus() == PlanStatus.CANCELLED || task.getStatus() == PlanStatus.EXPIRED) return;
        task.setStatus(statusByDate(task.getStartDate(), task.getEndDate()));
    }

    private LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    private TaskDetailResponse toDetailResponse(Task task) {
        return TaskDetailResponse.builder()
                .id(task.getId())
                .version(task.getVersion())
                .learningDurationMinutes(actionLogRepository.sumLearningMinutes(task.getUserId(), task.getId()))
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
