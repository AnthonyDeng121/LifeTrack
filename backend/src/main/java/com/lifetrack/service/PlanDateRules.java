package com.lifetrack.service;

import com.lifetrack.entity.PlanStatus;
import com.lifetrack.entity.Task;
import com.lifetrack.exception.BusinessException;
import java.time.LocalDate;

/** MySQL DATE 与所有计划入口共用的日期边界。 */
public final class PlanDateRules {
    private PlanDateRules() {}

    public static void requireSupported(LocalDate date) {
        if (date != null && (date.getYear() < 1000 || date.getYear() > 9999))
            throw new BusinessException(400, "日期年份必须在1000到9999之间");
    }

    public static void refresh(Task task, LocalDate today) {
        if (task.getStatus() != PlanStatus.NOT_STARTED && task.getStatus() != PlanStatus.IN_PROGRESS) return;
        task.setStatus(task.getEndDate().isBefore(today) ? PlanStatus.EXPIRED
                : task.getStartDate().isAfter(today) ? PlanStatus.NOT_STARTED : PlanStatus.IN_PROGRESS);
    }
}
