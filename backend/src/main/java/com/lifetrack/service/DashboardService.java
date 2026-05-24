package com.lifetrack.service;

import com.lifetrack.dto.DashboardStatsResponse;
import com.lifetrack.dto.SubTaskContributionResponse;

public interface DashboardService {
    /**
     * 获取看板统计数据
     */
    DashboardStatsResponse getStats();

    /**
     * 获取主任务下的子任务贡献度详情
     */
    SubTaskContributionResponse getTaskContribution(Long taskId);
}
