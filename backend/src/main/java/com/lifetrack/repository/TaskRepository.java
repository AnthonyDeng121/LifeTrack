package com.lifetrack.repository;

import com.lifetrack.entity.Task;
import com.lifetrack.entity.PlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from Task t where t.id = ?1")
    java.util.Optional<Task> findLockedById(Long id);
    @org.springframework.data.jpa.repository.Query("select t.id from Task t where t.userId = ?1 and t.status = ?2 order by t.id")
    List<Long> findIdsByUserIdAndStatus(Long userId, PlanStatus status);
    List<Task> findByUserId(Long userId);
    List<Task> findByUserIdAndStatus(Long userId, PlanStatus status);
}
