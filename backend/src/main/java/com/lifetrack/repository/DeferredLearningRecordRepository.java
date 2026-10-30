package com.lifetrack.repository;

import com.lifetrack.entity.DeferredLearningRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface DeferredLearningRecordRepository extends JpaRepository<DeferredLearningRecord, Long> {
    @Query("select r.taskId from DeferredLearningRecord r where r.id = ?1")
    Optional<Long> findTaskIdById(Long id);
    Optional<DeferredLearningRecord> findByUserIdAndRequestId(Long userId, String requestId);
    @Query("select r from DeferredLearningRecord r where r.userId=:userId and r.status in :statuses and (:taskId is null or r.taskId=:taskId)")
    org.springframework.data.domain.Page<DeferredLearningRecord> findPendingPage(Long userId, Long taskId, java.util.Collection<DeferredLearningRecord.Status> statuses, org.springframework.data.domain.Pageable pageable);
    List<DeferredLearningRecord> findByUserIdOrderByRecordedAtDesc(Long userId);
    List<DeferredLearningRecord> findByUserIdAndTaskId(Long userId, Long taskId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DeferredLearningRecord r where r.id = ?1")
    Optional<DeferredLearningRecord> findLockedById(Long id);
    void deleteByTaskId(Long taskId);
}
