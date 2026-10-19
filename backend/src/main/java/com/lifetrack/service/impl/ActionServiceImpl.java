package com.lifetrack.service.impl;

import com.lifetrack.common.UserContext;
import com.lifetrack.dto.*;
import com.lifetrack.entity.ActionLog;
import com.lifetrack.entity.CheckInRecord;
import com.lifetrack.repository.CheckInRecordRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import com.lifetrack.entity.SubTask;
import com.lifetrack.entity.Task;
import com.lifetrack.entity.PlanStatus;
import com.lifetrack.exception.BusinessException;
import com.lifetrack.repository.ActionLogRepository;
import com.lifetrack.repository.SubTaskRepository;
import com.lifetrack.repository.TaskRepository;
import com.lifetrack.service.ActionService;
import com.lifetrack.service.AIService;
import com.lifetrack.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActionServiceImpl implements ActionService {

    private final ActionLogRepository actionLogRepository;
    private final TaskRepository taskRepository;
    private final SubTaskRepository subTaskRepository;
    private final TaskService taskService;
    private final AIService aiService;
    private final CheckInRecordRepository checkInRecordRepository;
    private final com.lifetrack.repository.DeferredLearningRecordRepository deferredRecords;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ActionSyncResponse syncAction(ActionSyncRequest request) {
        return applyRecord(request, java.time.LocalDateTime.now(BUSINESS_ZONE), null);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ActionSyncResponse syncHistoricalAction(ActionSyncRequest request, java.time.LocalDateTime recordedAt, int durationMinutes) {
        return applyRecord(request, recordedAt, durationMinutes);
    }

    private ActionSyncResponse applyRecord(ActionSyncRequest request, java.time.LocalDateTime recordedAt, Integer fixedDuration) {
        Long userId = UserContext.getUserId();
        boolean historical = fixedDuration != null;
        log.info("User {} is syncing action: {}", userId, request.getRawInput());

        LocalDate today = recordedAt.toLocalDate();
        // 按固定顺序锁定计划，避免并发覆盖进度和重复生成打卡。
        List<Task> activeTasks = new ArrayList<>();
        List<Long> candidates = historical && request.getTaskId() != null ? List.of(request.getTaskId())
                : taskRepository.findIdsByUserIdAndStatus(userId, PlanStatus.IN_PROGRESS);
        if (request.getTaskId() != null && !candidates.contains(request.getTaskId())) {
            throw new BusinessException(400, "所选计划不存在、无权访问或当前不可记录");
        }
        candidates.stream().filter(id -> request.getTaskId() == null || id.equals(request.getTaskId()))
                .sorted().forEach(candidate -> {
                    Task task = taskRepository.findLockedById(candidate).orElse(null);
                    if (task != null && task.getUserId().equals(userId) && (task.getStatus() == PlanStatus.IN_PROGRESS || historical && task.getStatus() != PlanStatus.CANCELLED)
                            && task.getStartDate() != null && task.getEndDate() != null
                            && !today.isBefore(task.getStartDate()) && !today.isAfter(task.getEndDate())) {
                        activeTasks.add(task);
                    }
                });
        if (activeTasks.isEmpty()) throw new BusinessException(400, "没有处于有效周期内的进行中计划，请先创建或调整计划");
        Map<Long, Task> taskMap = activeTasks.stream().collect(Collectors.toMap(Task::getId, t -> t));
        Map<Long, SubTask> eligible = new LinkedHashMap<>();
        List<SubTask> aiCandidates = new ArrayList<>();
        for (Task task : activeTasks) {
            for (SubTask step : subTaskRepository.findByTaskIdOrderByOrderNumAsc(task.getId())) {
                if (step.getCurrentProgress().compareTo(BigDecimal.ONE) >= 0) continue;
                eligible.put(step.getId(), step);
                aiCandidates.add(SubTask.builder().id(step.getId()).taskId(task.getId())
                        .content("计划：" + task.getTitle() + "；步骤：" + step.getContent())
                        .currentProgress(step.getCurrentProgress()).build());
            }
        }
        if (eligible.isEmpty()) throw new BusinessException(400, "没有未完成的学习步骤，请先添加或拆解步骤");
        AIMatchResult aiResult = aiService.analyzeAction(request.getRawInput(), request.getDurationInput(), aiCandidates);
        if (!historical && !today.equals(LocalDate.now(BUSINESS_ZONE))) {
            throw new BusinessException(409, "日期已变化，请重新提交今日记录");
        }
        List<ActionSyncResponse.TaskUpdateDetail> updates = new ArrayList<>();
        Set<Long> matchedSteps = new HashSet<>();
        Set<Long> processedPlans = new HashSet<>();
        int duration = fixedDuration != null ? fixedDuration : Math.max(0, aiResult.getDurationMinutes() == null ? 0 : aiResult.getDurationMinutes());
        List<AIMatchResult.MatchDetail> rawMatches = aiResult.getMatches() == null ? Collections.emptyList() : aiResult.getMatches();
        // 每个被推进的步骤必须有各自独立的学习事实，不能把同一句笼统记录分摊给整门课程。
        Map<Long, AIMatchResult.MatchDetail> uniqueMatches = new LinkedHashMap<>();
        for (var match : rawMatches) {
            if (match != null && match.getIncrement() != null && match.getIncrement().signum() > 0
                    && eligible.containsKey(match.getSubTaskId())) uniqueMatches.putIfAbsent(match.getSubTaskId(), match);
        }
        if (uniqueMatches.size() > 1 && !hasIndependentEvidence(request.getRawInput(), uniqueMatches.values())) {
            return ActionSyncResponse.builder().updates(Collections.emptyList()).matchingAmbiguous(true)
                    .aiAnalysis("这条记录不足以分别证明多个步骤的学习内容，未保存或改变进度。请补充具体知识点与实际动作，例如‘整理了表、主键和外键的概念笔记’；记录多项内容时请分别描述。")
                    .build();
        }
        rawMatches = new ArrayList<>(uniqueMatches.values());
        long matchCount = rawMatches.stream().filter(Objects::nonNull)
                .filter(m -> m.getIncrement() != null && m.getIncrement().signum() > 0 && eligible.containsKey(m.getSubTaskId()))
                .map(AIMatchResult.MatchDetail::getSubTaskId).distinct().count();
        for (AIMatchResult.MatchDetail match : rawMatches) {
            if (match == null || match.getIncrement() == null || match.getIncrement().signum() <= 0) continue;
            SubTask step = eligible.get(match.getSubTaskId());
            if (step == null || !matchedSteps.add(step.getId())) continue;
            Task task = taskMap.get(step.getTaskId());
            // AI 只提供建议；后端限制贡献并保存实际增量，保证封顶与撤销准确。
            String evidence = match.getCompletionEvidence();
            boolean completed = match.isCompleted() && evidence != null && evidence.trim().length() >= 8
                    && request.getRawInput().contains(evidence.trim());
            // 普通投入估算最多95%；达到100%需要用户原话中的具体完成证据，或详情页手动确认。
            BigDecimal remaining = (completed ? BigDecimal.ONE : new BigDecimal("0.95"))
                    .subtract(step.getCurrentProgress()).max(BigDecimal.ZERO);
            BigDecimal increment = (completed ? remaining : match.getIncrement().min(new BigDecimal("0.30")).min(remaining))
                    .setScale(2, java.math.RoundingMode.DOWN);
            // 达到估算上限后仍可记录有效学习和当天打卡，仅保持进度不再增长。
            boolean atEstimateLimit = step.getCurrentProgress().compareTo(new BigDecimal("0.95")) >= 0;
            if (increment.signum() <= 0 && !atEstimateLimit) continue;
            boolean firstPlanMatch = processedPlans.add(task.getId());
            boolean checkedInNow = false;
            if (firstPlanMatch && !checkInRecordRepository.existsByUserIdAndTaskIdAndCheckInDate(userId, task.getId(), today)) {
                checkInRecordRepository.saveAndFlush(CheckInRecord.builder().userId(userId).taskId(task.getId())
                        .checkInDate(today).content(request.getRawInput().trim()).durationMinutes(duration)
                        .automatic(true).build());
                checkedInNow = true;
            }
            // 按匹配步骤分摊时长，总和等于本次输入时长，避免多计划重复统计。
            int allocatedDuration = (int) (duration / matchCount + (updates.size() < duration % matchCount ? 1 : 0));
            actionLogRepository.save(ActionLog.builder().userId(userId).taskId(task.getId()).subTaskId(step.getId())
                    .rawInput(request.getRawInput().trim()).contribution(increment).category(task.getCategory())
                    .durationMinutes(allocatedDuration).aiAnalysis(aiResult.getAiAnalysis())
                    .createdAt(recordedAt).build());
            step.setCurrentProgress(step.getCurrentProgress().add(increment));
            step.setIsCompleted(step.getCurrentProgress().compareTo(BigDecimal.ONE) >= 0 ? 1 : 0);
            subTaskRepository.save(step);
            taskService.updateTaskProgress(task.getId());
            updates.add(ActionSyncResponse.TaskUpdateDetail.builder().taskId(task.getId()).subTaskId(step.getId())
                    .taskTitle(task.getTitle()).subTaskTitle(step.getContent()).checkedInNow(checkedInNow)
                    .increment(increment.multiply(new BigDecimal("100")))
                    .newTotalProgress(task.getTotalProgress().multiply(new BigDecimal("100"))).build());
        }
        for (Long taskId : processedPlans) refreshAutomaticAttendance(userId, taskId, today);
        return ActionSyncResponse.builder().updates(updates).analysisUnavailable(aiResult.isUnavailable())
                .aiAnalysis(aiResult.getAiAnalysis() == null ? "未匹配到有效学习步骤，未保存记录或打卡" : aiResult.getAiAnalysis()).build();
    }

    @Override
    public PageResponse<ActionHistoryResponse> getHistory(Pageable pageable) {
        Long userId = UserContext.getUserId();
        Page<ActionLog> page = actionLogRepository.findByUserId(userId, pageable);

        // 获取涉及到的任务 ID 列表，用于批量查询任务标题
        Set<Long> taskIds = page.getContent().stream()
                .map(ActionLog::getTaskId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        
        Map<Long, String> taskTitleMap = taskRepository.findAllById(taskIds).stream()
                .collect(Collectors.toMap(Task::getId, Task::getTitle));

        List<ActionHistoryResponse> list = page.getContent().stream()
                .map(log -> ActionHistoryResponse.builder()
                        .id(log.getId())
                        .rawInput(log.getRawInput())
                        .contribution(log.getContribution().multiply(new BigDecimal("100")))
                        .aiAnalysis(log.getAiAnalysis())
                        .taskTitle(taskTitleMap.getOrDefault(log.getTaskId(), "未知任务"))
                        .category(log.getCategory() != null ? log.getCategory().name() : null)
                        .createdAt(log.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return PageResponse.of(page.getTotalElements(), list);
    }

    private boolean hasIndependentEvidence(String input, java.util.Collection<AIMatchResult.MatchDetail> matches) {
        List<int[]> occupied = new ArrayList<>();
        for (var match : matches) {
            String evidence = match.getMatchEvidence();
            if (evidence == null || evidence.trim().length() < 4) return false;
            evidence = evidence.trim();
            int start = input.indexOf(evidence);
            if (start < 0) return false;
            int end = start + evidence.length();
            for (int[] span : occupied) if (start < span[1] && end > span[0]) return false;
            occupied.add(new int[]{start, end});
        }
        return true;
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void deleteAction(Long actionId) {
        Long userId = UserContext.getUserId();
        ActionLog actionLog = actionLogRepository.findById(actionId)
                .orElseThrow(() -> new BusinessException(404, "记录不存在"));
        
        if (!actionLog.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权删除该记录");
        }

        if (actionLog.getTaskId() != null) taskRepository.findLockedById(actionLog.getTaskId());
        if (!actionLogRepository.existsById(actionId)) {
            throw new BusinessException(404, "记录已撤销，请勿重复操作");
        }
        // 如果该记录关联了任务和子任务，需要回退进度
        if (actionLog.getSubTaskId() != null) {
            SubTask subTask = subTaskRepository.findById(actionLog.getSubTaskId()).orElse(null);
            if (subTask != null) {
                // 回退进度
                BigDecimal newProgress = subTask.getCurrentProgress().subtract(actionLog.getContribution());
                if (newProgress.compareTo(BigDecimal.ZERO) < 0) {
                    newProgress = BigDecimal.ZERO;
                }
                subTask.setCurrentProgress(newProgress);
                
                // 如果回退后进度小于 1.0，标记为未完成
                if (newProgress.compareTo(BigDecimal.ONE) < 0) {
                    subTask.setIsCompleted(0);
                }
                subTaskRepository.save(subTask);

                // 更新主任务总进度
                taskRepository.findById(subTask.getTaskId()).ifPresent(parent -> {
                    if (parent.getStatus() == PlanStatus.COMPLETED) parent.setStatus(PlanStatus.IN_PROGRESS);
                });
                taskService.updateTaskProgress(subTask.getTaskId());
            }
        }

        actionLogRepository.delete(actionLog);
        actionLogRepository.flush();
        if (actionLog.getTaskId() != null && actionLog.getCreatedAt() != null) {
            refreshAutomaticAttendance(userId, actionLog.getTaskId(), actionLog.getCreatedAt().toLocalDate());
        }
    }

    @Override
    @Transactional
    public void refreshAttendance(Long taskId, LocalDate date) {
        Long userId = UserContext.getUserId();
        Task task = taskRepository.findLockedById(taskId).orElseThrow(() -> new BusinessException(404, "计划不存在"));
        if (!task.getUserId().equals(userId)) throw new BusinessException(403, "无权操作该计划");
        refreshAutomaticAttendance(userId, taskId, date);
    }

    private void refreshAutomaticAttendance(Long userId, Long taskId, LocalDate date) {
        List<com.lifetrack.entity.DeferredLearningRecord> deferred = deferredRecords.findByUserIdAndTaskId(userId, taskId).stream()
                .filter(r -> r.getRecordedAt().toLocalDate().equals(date)).toList();
        checkInRecordRepository.findByUserIdAndTaskIdAndCheckInDate(userId, taskId, date)
                .filter(r -> Boolean.TRUE.equals(r.getAutomatic()) || !deferred.isEmpty()).ifPresent(record -> {
                    List<ActionLog> remaining = actionLogRepository.findByTaskId(taskId).stream()
                            .filter(a -> a.getUserId().equals(userId) && a.getCreatedAt().toLocalDate().equals(date)).toList();
                    List<com.lifetrack.entity.DeferredLearningRecord> awaiting = deferred.stream()
                            .filter(r -> r.getStatus() != com.lifetrack.entity.DeferredLearningRecord.Status.APPLIED).toList();
                    if (remaining.isEmpty() && deferred.isEmpty() && Boolean.TRUE.equals(record.getAutomatic())) {
                        checkInRecordRepository.delete(record);
                    } else {
                        record.setDurationMinutes(remaining.stream().mapToInt(a -> a.getDurationMinutes() == null ? 0 : a.getDurationMinutes()).sum()
                                + awaiting.stream().mapToInt(com.lifetrack.entity.DeferredLearningRecord::getDurationMinutes).sum());
                        if (!remaining.isEmpty()) record.setContent(remaining.get(0).getRawInput());
                        else if (!deferred.isEmpty()) record.setContent(deferred.get(0).getContent());
                        checkInRecordRepository.save(record);
                    }
                });
    }
}
