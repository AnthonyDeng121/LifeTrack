package com.lifetrack.service.impl;

import com.lifetrack.common.UserContext;
import com.lifetrack.dto.*;
import com.lifetrack.entity.ActionLog;
import com.lifetrack.entity.SubTask;
import com.lifetrack.entity.Task;
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

    @Override
    @Transactional
    public ActionSyncResponse syncAction(ActionSyncRequest request) {
        Long userId = UserContext.getUserId();
        log.info("User {} is syncing action: {}", userId, request.getRawInput());

        // 1. 获取用户所有进行中的主任务及其子任务
        List<Task> activeTasks = taskRepository.findByUserIdAndStatus(userId, 0);
        if (activeTasks.isEmpty()) {
            throw new BusinessException(400, "请先创建任务再进行同步");
        }

        List<SubTask> allCandidateSubTasks = activeTasks.stream()
                .flatMap(task -> subTaskRepository.findByTaskIdOrderByOrderNumAsc(task.getId()).stream())
                .collect(Collectors.toList());

        if (allCandidateSubTasks.isEmpty()) {
            throw new BusinessException(400, "没有任何已拆解的任务，请先进行任务拆解");
        }

        // 2. 调用 AI 进行 1 对 N 匹配分析
        AIMatchResult aiResult = aiService.analyzeAction(request.getRawInput(), request.getDurationInput(), allCandidateSubTasks);
        
        if (aiResult.getMatches() == null || aiResult.getMatches().isEmpty()) {
            return ActionSyncResponse.builder()
                    .updates(Collections.emptyList())
                    .aiAnalysis(aiResult.getAiAnalysis() != null ? aiResult.getAiAnalysis() : "AI 未能匹配到任何相关任务")
                    .build();
        }

        // 3. 处理所有匹配到的子任务更新
        List<ActionSyncResponse.TaskUpdateDetail> updateDetails = new ArrayList<>();
        Map<Long, Task> taskMap = activeTasks.stream().collect(Collectors.toMap(Task::getId, t -> t));

        // 对 AI 返回的匹配项进行去重，防止同一子任务被多次更新
        Map<Long, AIMatchResult.MatchDetail> uniqueMatches = new LinkedHashMap<>();
        for (AIMatchResult.MatchDetail match : aiResult.getMatches()) {
            uniqueMatches.putIfAbsent(match.getSubTaskId(), match);
        }

        for (AIMatchResult.MatchDetail match : uniqueMatches.values()) {
            SubTask subTask = subTaskRepository.findById(match.getSubTaskId()).orElse(null);
            if (subTask == null) continue;

            Task task = taskMap.get(subTask.getTaskId());
            if (task == null) continue;

            // 持久化行为日志
            ActionLog actionLog = ActionLog.builder()
                    .userId(userId)
                    .taskId(task.getId())
                    .subTaskId(subTask.getId())
                    .rawInput(request.getRawInput())
                    .contribution(match.getIncrement())
                    .category(task.getCategory())
                    .durationMinutes(aiResult.getDurationMinutes() != null ? aiResult.getDurationMinutes() : 0) // 由 AI 解析模糊时长输入
                    .aiAnalysis(aiResult.getAiAnalysis())
                    .build();
            actionLogRepository.save(actionLog);

            // 更新子任务进度
            subTask.setCurrentProgress(subTask.getCurrentProgress().add(match.getIncrement()));
            if (subTask.getCurrentProgress().compareTo(BigDecimal.ONE) >= 0) {
                subTask.setCurrentProgress(BigDecimal.ONE);
                subTask.setIsCompleted(1);
            }
            subTaskRepository.save(subTask);

            // 更新主任务总进度
            taskService.updateTaskProgress(task.getId());
            
            // 重新获取更新后的进度
            Task updatedTask = taskRepository.findById(task.getId()).orElse(task);
            updateDetails.add(ActionSyncResponse.TaskUpdateDetail.builder()
                    .taskTitle(updatedTask.getTitle())
                    .increment(match.getIncrement().multiply(new BigDecimal("100")))
                    .newTotalProgress(updatedTask.getTotalProgress().multiply(new BigDecimal("100")))
                    .build());
        }

        return ActionSyncResponse.builder()
                .updates(updateDetails)
                .aiAnalysis(aiResult.getAiAnalysis())
                .build();
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

    @Override
    @Transactional
    public void deleteAction(Long actionId) {
        Long userId = UserContext.getUserId();
        ActionLog actionLog = actionLogRepository.findById(actionId)
                .orElseThrow(() -> new BusinessException(404, "记录不存在"));
        
        if (!actionLog.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权删除该记录");
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
                taskService.updateTaskProgress(subTask.getTaskId());
            }
        }

        actionLogRepository.delete(actionLog);
    }
}
