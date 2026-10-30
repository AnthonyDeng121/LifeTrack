package com.lifetrack.repository;

import com.lifetrack.entity.ActionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

@Repository
public interface ActionLogRepository extends JpaRepository<ActionLog, Long> {
    interface RemovalTarget { Long getUserId(); Long getTaskId(); }
    @Query("select a.userId as userId, a.taskId as taskId from ActionLog a where a.id = ?1")
    java.util.Optional<RemovalTarget> findRemovalTarget(Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ActionLog a where a.id = ?1")
    java.util.Optional<ActionLog> findLockedById(Long id);
    @Modifying(flushAutomatically = true)
    @Query("update ActionLog a set a.subTaskId = null where a.subTaskId = ?1")
    void detachStepProgress(Long stepId);
    List<ActionLog> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);
    List<ActionLog> findByTaskId(Long taskId);
    Page<ActionLog> findByUserId(Long userId, Pageable pageable);
    Page<ActionLog> findByUserIdAndTaskId(Long userId, Long taskId, Pageable pageable);

    @Query("select coalesce(sum(a.durationMinutes), 0) from ActionLog a where a.userId = ?1 and a.taskId = ?2")
    long sumLearningMinutes(Long userId, Long taskId);

    interface DayTotals {
        long getRecordCount();
        long getMinutes();
    }

    @Query("select count(a) as recordCount, coalesce(sum(a.durationMinutes), 0) as minutes from ActionLog a "
            + "where a.userId = ?1 and a.taskId = ?2 and a.createdAt >= ?3 and a.createdAt < ?4")
    DayTotals aggregateDay(Long userId, Long taskId, LocalDateTime from, LocalDateTime until);

    java.util.Optional<ActionLog> findFirstByUserIdAndTaskIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
            Long userId, Long taskId, LocalDateTime from, LocalDateTime until);

    @Modifying
    @Query("delete from ActionLog a where a.taskId = ?1")
    void deleteByTaskId(Long taskId);
}
