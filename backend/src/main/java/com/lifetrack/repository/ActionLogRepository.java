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
    List<ActionLog> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);
    List<ActionLog> findByTaskId(Long taskId);
    Page<ActionLog> findByUserId(Long userId, Pageable pageable);

    @Modifying
    @Query("delete from ActionLog a where a.taskId = ?1")
    void deleteByTaskId(Long taskId);
}
