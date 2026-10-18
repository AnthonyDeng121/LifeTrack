package com.lifetrack.repository;

import com.lifetrack.entity.DeferredLearningRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface DeferredLearningRecordRepository extends JpaRepository<DeferredLearningRecord, Long> {
    List<DeferredLearningRecord> findByUserIdOrderByRecordedAtDesc(Long userId);
    List<DeferredLearningRecord> findByUserIdAndTaskId(Long userId, Long taskId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DeferredLearningRecord r where r.id = ?1")
    Optional<DeferredLearningRecord> findLockedById(Long id);
    void deleteByTaskId(Long taskId);
}
