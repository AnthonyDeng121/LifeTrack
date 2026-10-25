package com.lifetrack.service;

import com.lifetrack.common.UserContext;
import com.lifetrack.dto.*;
import com.lifetrack.entity.*;
import com.lifetrack.exception.BusinessException;
import com.lifetrack.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeferredLearningRecordService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final DeferredLearningRecordRepository records;
    private final TaskRepository tasks;
    private final CheckInRecordRepository checkIns;
    private final ActionLogRepository logs;
    private final CheckInService checkInService;
    private final ActionService actions;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DeferredLearningRecordResponse save(DeferredLearningRecordRequest request) {
        Long userId = UserContext.getUserId();
        Task task = ownedLockedTask(request.getTaskId(), userId);
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDate today = now.toLocalDate();
        if (task.getStatus() != PlanStatus.IN_PROGRESS || today.isBefore(task.getStartDate()) || today.isAfter(task.getEndDate())) {
            throw new BusinessException(400, "仅有效周期内的进行中计划可以保存学习记录");
        }
        if (!checkIns.existsByUserIdAndTaskIdAndCheckInDate(userId, task.getId(), today)) {
            CheckInCreateRequest checkIn = new CheckInCreateRequest();
            checkIn.setTaskId(task.getId()); checkIn.setContent(request.getContent());
            checkIn.setDurationMinutes(request.getDurationMinutes());
            checkInService.create(checkIn);
        }
        DeferredLearningRecord record = records.save(DeferredLearningRecord.builder().userId(userId).taskId(task.getId())
                .content(request.getContent().trim()).durationMinutes(request.getDurationMinutes()).recordedAt(now)
                .status(DeferredLearningRecord.Status.PENDING).lastAnalysis("已保存，等待AI恢复后补分析").build());
        actions.refreshAttendance(task.getId(), today);
        return response(record, task.getTitle());
    }

    @Transactional(readOnly = true)
    public List<DeferredLearningRecordResponse> list() {
        Long userId = UserContext.getUserId();
        List<DeferredLearningRecord> entries = records.findByUserIdOrderByRecordedAtDesc(userId);
        Map<Long, String> titles = tasks.findAllById(entries.stream().map(DeferredLearningRecord::getTaskId).distinct().toList())
                .stream().filter(t -> t.getUserId().equals(userId)).collect(Collectors.toMap(Task::getId, Task::getTitle));
        return entries.stream().map(r -> response(r, titles.getOrDefault(r.getTaskId(), "已删除的计划"))).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ActionSyncResponse retry(Long id) {
        Long userId = UserContext.getUserId();
        DeferredLearningRecord record = records.findLockedById(id).orElseThrow(() -> new BusinessException(404, "待分析记录不存在"));
        if (!record.getUserId().equals(userId)) throw new BusinessException(403, "无权操作该记录");
        if (record.getStatus() == DeferredLearningRecord.Status.APPLIED) throw new BusinessException(409, "该记录已处理，请勿重复补进度");
        Task task = ownedLockedTask(record.getTaskId(), userId);
        if (task.getStatus() == PlanStatus.COMPLETED || task.getStatus() == PlanStatus.CANCELLED) {
            throw new BusinessException(400, "已完成或已取消计划不可补分析，计划不会重新打开");
        }
        ActionSyncRequest request = new ActionSyncRequest();
        request.setTaskId(record.getTaskId()); request.setRawInput(record.getContent());
        request.setDurationInput(record.getDurationMinutes() + "分钟");
        // 与进度写入共用事务。先标记已处理，汇总时不再重复计算这条待分析时长。
        record.setStatus(DeferredLearningRecord.Status.APPLIED);
        ActionSyncResponse result = actions.syncHistoricalAction(request, record.getRecordedAt(), record.getDurationMinutes());
        record.setStatus(result.isAnalysisUnavailable() ? DeferredLearningRecord.Status.PENDING
                : result.getUpdates().isEmpty() ? DeferredLearningRecord.Status.UNMATCHED : DeferredLearningRecord.Status.APPLIED);
        record.setLastAnalysis(result.getAiAnalysis());
        records.save(record);
        actions.refreshAttendance(record.getTaskId(), record.getRecordedAt().toLocalDate());
        return result;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int importLegacyCheckIns() {
        Long userId = UserContext.getUserId();
        int imported = 0;
        for (CheckInRecord checkIn : checkIns.findByUserIdAndCheckInDateBetweenOrderByCheckInDateDescCreatedAtDesc(
                userId, LocalDate.of(1970, 1, 1), LocalDate.now(ZONE)).stream()
                .sorted(Comparator.comparing(CheckInRecord::getTaskId).thenComparing(CheckInRecord::getCheckInDate)).toList()) {
            if (Boolean.TRUE.equals(checkIn.getAutomatic())) continue;
            ownedLockedTask(checkIn.getTaskId(), userId);
            LocalDate date = checkIn.getCheckInDate();
            boolean known = records.findByUserIdAndTaskId(userId, checkIn.getTaskId()).stream()
                    .anyMatch(r -> r.getRecordedAt().toLocalDate().equals(date));
            boolean progressed = logs.findByTaskId(checkIn.getTaskId()).stream()
                    .anyMatch(a -> a.getUserId().equals(userId) && a.getCreatedAt().toLocalDate().equals(date)
                            && a.getContribution().signum() > 0);
            if (known || progressed) continue;
            records.save(DeferredLearningRecord.builder().userId(userId).taskId(checkIn.getTaskId())
                    .content(checkIn.getContent()).durationMinutes(checkIn.getDurationMinutes())
                    .recordedAt(date.atTime(checkIn.getCreatedAt() == null ? LocalTime.NOON : checkIn.getCreatedAt().toLocalTime()))
                    .status(DeferredLearningRecord.Status.PENDING).lastAnalysis("从旧版手动打卡导入，等待补分析").build());
            imported++;
        }
        return imported;
    }

    private Task ownedLockedTask(Long id, Long userId) {
        Task task = tasks.findLockedById(id).orElseThrow(() -> new BusinessException(404, "计划不存在"));
        if (!task.getUserId().equals(userId)) throw new BusinessException(403, "无权操作该计划");
        return task;
    }
    private DeferredLearningRecordResponse response(DeferredLearningRecord r, String title) {
        return new DeferredLearningRecordResponse(r.getId(), r.getTaskId(), title, r.getContent(), r.getDurationMinutes(),
                r.getRecordedAt(), r.getStatus(), r.getLastAnalysis());
    }
}
