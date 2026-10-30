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
    private final org.springframework.transaction.support.TransactionOperations recordTransactions;
    private final DeferredLearningRecordRepository records;
    private final TaskRepository tasks;
    private final CheckInRecordRepository checkIns;
    private final ActionLogRepository logs;
    private final ActionService actions;

    @Transactional(readOnly = true)
    public PageResponse<DeferredLearningRecordResponse> pendingPage(Long taskId, int page, int size) {
        if (page < 0 || page > 1000000 || size < 1 || size > 100) throw new BusinessException(400, "页码应为0到1000000，每页1到100条");
        Long userId = UserContext.getUserId();
        if (taskId != null) {
            Task task = tasks.findById(taskId).orElseThrow(() -> new BusinessException(404, "计划不存在"));
            if (!task.getUserId().equals(userId)) throw new BusinessException(403, "无权查看该计划");
        }
        var entries = records.findPendingPage(userId, taskId, List.of(DeferredLearningRecord.Status.PENDING, DeferredLearningRecord.Status.UNMATCHED),
                org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "recordedAt", "id")));
        Map<Long, Task> parents = tasks.findAllById(entries.getContent().stream().map(DeferredLearningRecord::getTaskId).distinct().toList())
                .stream().filter(t -> t.getUserId().equals(userId)).collect(Collectors.toMap(Task::getId, t -> t));
        return PageResponse.of(entries.getTotalElements(), entries.getContent().stream()
                .map(r -> response(r, parents.get(r.getTaskId()))).toList());
    }

    /** 原文先提交到数据库；外部AI失败不会回滚原文，也不会产生学习时长或打卡。 */
    public ActionSyncResponse submit(ActionSyncRequest request) {
        var input = new DeferredLearningRecordRequest();
        input.setTaskId(request.getTaskId());
        input.setContent(request.getRawInput());
        input.setDurationMinutes(request.getDurationMinutes());
        input.setRequestId(request.getRequestId());
        DeferredLearningRecordResponse saved = recordTransactions.execute(tx -> save(input));
        if (saved.status() == DeferredLearningRecord.Status.APPLIED)
            return ActionSyncResponse.builder().recordId(saved.id()).replayed(true).updates(List.of())
                    .aiAnalysis("该次提交已成功处理，本次未重复计时或打卡").build();
        return retry(saved.id());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DeferredLearningRecordResponse save(DeferredLearningRecordRequest request) {
        Long userId = UserContext.getUserId();
        Task task = ownedLockedTask(request.getTaskId(), userId);
        String requestId = request.getRequestId() == null ? UUID.randomUUID().toString() : request.getRequestId().toLowerCase(java.util.Locale.ROOT);
        Optional<DeferredLearningRecord> existing = records.findByUserIdAndRequestId(userId, requestId);
        if (existing.isPresent()) {
            DeferredLearningRecord previous = existing.get();
            if (previous.getStatus() == DeferredLearningRecord.Status.DISCARDED)
                throw new BusinessException(409, "该次记录已删除或替换，不能重新处理旧提交");
            if (!previous.getTaskId().equals(request.getTaskId()) || !previous.getContent().equals(request.getContent().trim())
                    || !previous.getDurationMinutes().equals(request.getDurationMinutes()))
                throw new BusinessException(409, "提交标识已用于其他学习内容，请重新提交");
            return response(previous, task);
        }
        // MySQL DATETIME基线精度为秒，先截断而非让数据库四舍五入，防止23:59:59跨到次日。
        LocalDateTime now = LocalDateTime.now(ZONE).withNano(0);
        LocalDate today = now.toLocalDate();
        PlanDateRules.refresh(task, today);
        if (task.getStatus() != PlanStatus.IN_PROGRESS || today.isBefore(task.getStartDate()) || today.isAfter(task.getEndDate())) {
            throw new BusinessException(400, "仅有效周期内的进行中计划可以保存学习记录");
        }
        if (request.getDurationMinutes() == null || request.getDurationMinutes() < 1 || request.getDurationMinutes() > 1440)
            throw new BusinessException(400, "学习时长必须为1到1440整数分钟");
        DeferredLearningRecord record = records.save(DeferredLearningRecord.builder().userId(userId).taskId(task.getId()).requestId(requestId)
                .content(request.getContent().trim()).durationMinutes(request.getDurationMinutes()).recordedAt(now)
                .status(DeferredLearningRecord.Status.PENDING).lastAnalysis("记录已保存，尚未分析成功，时长未计入").build());
        return response(record, task);
    }

    public ActionSyncResponse retry(Long id) {
        Long userId = UserContext.getUserId();
        DeferredLearningRecord record = records.findById(id).orElseThrow(() -> new BusinessException(404, "待分析记录不存在"));
        if (!record.getUserId().equals(userId)) throw new BusinessException(403, "无权操作该记录");
        requirePending(record);
        Task task = tasks.findById(record.getTaskId()).orElseThrow(() -> new BusinessException(404, "计划不存在"));
        requireRetryable(task, record);
        ActionSyncRequest request = new ActionSyncRequest();
        request.setTaskId(record.getTaskId()); request.setRawInput(record.getContent());
        request.setDurationMinutes(record.getDurationMinutes());
        PreparedLearningRecord prepared = actions.prepareHistoricalAction(request, record.getRecordedAt(), record.getDurationMinutes());
        return recordTransactions.execute(tx -> applyRetry(id, userId, record, prepared));
    }

    private ActionSyncResponse applyRetry(Long id, Long userId, DeferredLearningRecord snapshot, PreparedLearningRecord prepared) {
        Task parent = ownedLockedTask(snapshot.getTaskId(), userId);
        DeferredLearningRecord record = records.findLockedById(id).orElseThrow(() -> new BusinessException(404, "待分析记录不存在"));
        if (!record.getUserId().equals(userId)) throw new BusinessException(403, "无权操作该记录");
        requirePending(record);
        requireRetryable(parent, record);
        // 与进度写入共用事务。先标记已处理，汇总时不再重复计算这条待分析时长。
        record.setStatus(DeferredLearningRecord.Status.APPLIED);
        ActionSyncResponse result = actions.syncPreparedAction(prepared);
        record.setStatus(result.isAnalysisUnavailable() ? DeferredLearningRecord.Status.PENDING
                : result.getUpdates().isEmpty() ? DeferredLearningRecord.Status.UNMATCHED : DeferredLearningRecord.Status.APPLIED);
        record.setLastAnalysis(result.getAiAnalysis());
        records.save(record);
        actions.refreshAttendance(record.getTaskId(), record.getRecordedAt().toLocalDate());
        result.setRecordId(id);
        return result;
    }

    /** 修改生成新记录；原提交仅留防重凭证，避免旧请求覆盖修正后的内容。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DeferredLearningRecordResponse update(Long id, DeferredLearningRecordUpdateRequest request) {
        Long userId = UserContext.getUserId();
        DeferredLearningRecord previous = ownedPendingLocked(id, userId);
        Task parent = tasks.findById(previous.getTaskId()).orElseThrow(() -> new BusinessException(404, "计划不存在"));
        requireRetryable(parent, previous);
        if (request.getContent() == null || request.getContent().isBlank() || request.getContent().trim().length() > 500)
            throw new BusinessException(400, "学习内容应为1到500字");
        if (request.getDurationMinutes() == null || request.getDurationMinutes() < 1 || request.getDurationMinutes() > 1440)
            throw new BusinessException(400, "学习时长必须为1到1440整数分钟");
        DeferredLearningRecord replacement = records.save(DeferredLearningRecord.builder().userId(userId)
                .taskId(previous.getTaskId()).requestId(UUID.randomUUID().toString()).recordedAt(previous.getRecordedAt())
                .content(request.getContent().trim()).durationMinutes(request.getDurationMinutes())
                .status(DeferredLearningRecord.Status.PENDING).lastAnalysis("内容已修正，等待重新分析，时长尚未计入").build());
        discard(previous);
        return response(replacement, parent);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long id) {
        discard(ownedPendingLocked(id, UserContext.getUserId()));
    }

    private DeferredLearningRecord ownedPendingLocked(Long id, Long userId) {
        Long taskId = records.findTaskIdById(id).orElseThrow(() -> new BusinessException(404, "待分析记录不存在"));
        ownedLockedTask(taskId, userId); // 先读取标量定位父计划，避免等待锁前将旧记录实体放入一级缓存。
        DeferredLearningRecord current = records.findLockedById(id).orElseThrow(() -> new BusinessException(404, "待分析记录不存在"));
        if (!current.getUserId().equals(userId)) throw new BusinessException(403, "无权操作该记录");
        requirePending(current);
        return current;
    }

    private void discard(DeferredLearningRecord record) {
        record.setStatus(DeferredLearningRecord.Status.DISCARDED);
        record.setContent("[已移除]"); record.setDurationMinutes(0); record.setLastAnalysis("已移除，仅保留提交防重凭证");
        records.save(record);
    }

    private void requirePending(DeferredLearningRecord record) {
        if (record.getStatus() == DeferredLearningRecord.Status.APPLIED)
            throw new BusinessException(409, "该记录已处理，请勿重复补进度");
        if (record.getStatus() == DeferredLearningRecord.Status.DISCARDED)
            throw new BusinessException(409, "该记录已删除或替换，请刷新列表");
    }

    private String retryBlockedReason(Task task, DeferredLearningRecord record) {
        if (task == null) return "计划不存在，无法重新分析";
        LocalDate today = LocalDate.now(ZONE);
        if (task.getStatus().isClosed() || task.getStatus() == PlanStatus.EXPIRED
                || today.isAfter(task.getEndDate())) return "计划已关闭或过期，仅可查看或删除待分析记录";
        if (task.getStatus() != PlanStatus.IN_PROGRESS || today.isBefore(task.getStartDate()))
            return "计划尚未进行，暂不可修改或重新分析";
        LocalDate original = record.getRecordedAt().toLocalDate();
        if (original.isBefore(task.getStartDate()) || original.isAfter(task.getEndDate()))
            return "原记录日期已在计划周期外，仅可查看或删除";
        return "";
    }

    private void requireRetryable(Task task, DeferredLearningRecord record) {
        String reason = retryBlockedReason(task, record);
        if (!reason.isEmpty()) throw new BusinessException(400, reason);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int importLegacyCheckIns() {
        Long userId = UserContext.getUserId();
        int imported = 0;
        for (CheckInRecord checkIn : checkIns.findByUserIdAndCheckInDateBetweenOrderByCheckInDateDescCreatedAtDesc(
                userId, LocalDate.of(1970, 1, 1), LocalDate.now(ZONE)).stream()
                .sorted(Comparator.comparing(CheckInRecord::getTaskId).thenComparing(CheckInRecord::getCheckInDate)).toList()) {
            if (Boolean.TRUE.equals(checkIn.getAutomatic()) || checkIn.getContent() == null || checkIn.getContent().isBlank()) continue;
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
    private DeferredLearningRecordResponse response(DeferredLearningRecord r, Task task) {
        String reason = r.getStatus() == DeferredLearningRecord.Status.APPLIED || r.getStatus() == DeferredLearningRecord.Status.DISCARDED
                ? "记录已处理或移除" : retryBlockedReason(task, r);
        return new DeferredLearningRecordResponse(r.getId(), r.getTaskId(), task == null ? "已删除的计划" : task.getTitle(), r.getContent(), r.getDurationMinutes(),
                r.getRecordedAt(), r.getStatus(), r.getLastAnalysis(), reason.isEmpty(), reason);
    }
}
