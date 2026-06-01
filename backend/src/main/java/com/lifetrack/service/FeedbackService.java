package com.lifetrack.service;

import com.lifetrack.dto.FeedbackReportResponse;
import com.lifetrack.dto.UserMoodUpdateRequest;

public interface FeedbackService {
    /**
     * 获取周/月度反馈报告
     */
    FeedbackReportResponse getFeedbackReport();

    /**
     * 更新用户情绪/焦虑等级
     */
    void updateMood(UserMoodUpdateRequest request);
}
