package com.lifetrack.service;

import com.lifetrack.dto.ActionSyncRequest;
import com.lifetrack.dto.ActionSyncResponse;

public interface ActionService {
    /**
     * 同步行为记录
     * 接收用户自然语言输入，调用 AI 判定贡献度并更新进度
     */
    ActionSyncResponse syncAction(ActionSyncRequest request);
}
