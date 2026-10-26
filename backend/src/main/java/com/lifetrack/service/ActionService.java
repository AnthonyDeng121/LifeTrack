package com.lifetrack.service;

import com.lifetrack.dto.ActionHistoryResponse;
import com.lifetrack.dto.ActionSyncRequest;
import com.lifetrack.dto.ActionSyncResponse;
import com.lifetrack.dto.PageResponse;
import org.springframework.data.domain.Pageable;

public interface ActionService {
    /**
     * 同步行为记录
     * 接收用户自然语言输入，调用 AI 判定贡献度并更新进度
     */
    ActionSyncResponse syncAction(ActionSyncRequest request);

    // 仅供已持久化的故障记录补分析调用，日期不能由外部请求自行指定。
    ActionSyncResponse syncHistoricalAction(ActionSyncRequest request, java.time.LocalDateTime recordedAt, int durationMinutes);
    PreparedLearningRecord prepareHistoricalAction(ActionSyncRequest request, java.time.LocalDateTime recordedAt, int durationMinutes);
    ActionSyncResponse syncPreparedAction(PreparedLearningRecord prepared);
    void refreshAttendance(Long taskId, java.time.LocalDate date);

    /**
     * 获取行为日志列表
     */
    PageResponse<ActionHistoryResponse> getHistory(Pageable pageable);

    /**
     * 删除/撤销行为记录
     */
    void deleteAction(Long actionId);
}
