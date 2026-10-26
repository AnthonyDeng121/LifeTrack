package com.lifetrack.service;

import com.lifetrack.entity.PlanStatus;
import com.lifetrack.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@Service
@EnableScheduling
@RequiredArgsConstructor
public class PlanDateTransitionService {
    private final TaskRepository tasks;

    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Shanghai")
    public void refresh() {
        var today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        tasks.expireDatedPlans(PlanStatus.EXPIRED, PlanStatus.IN_PROGRESS, PlanStatus.NOT_STARTED, today);
        tasks.activateDatedPlans(PlanStatus.IN_PROGRESS, PlanStatus.NOT_STARTED, today);
    }
}
