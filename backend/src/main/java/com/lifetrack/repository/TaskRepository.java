package com.lifetrack.repository;

import com.lifetrack.entity.Task;
import com.lifetrack.entity.PlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Task t set t.status = ?1, t.version = t.version + 1 where t.status in (?2, ?3) and t.endDate < ?4")
    int expireDatedPlans(PlanStatus expired, PlanStatus active, PlanStatus notStarted, java.time.LocalDate today);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Task t set t.status = ?1, t.version = t.version + 1 where t.status = ?2 and t.startDate <= ?3 and t.endDate >= ?3")
    int activateDatedPlans(PlanStatus active, PlanStatus notStarted, java.time.LocalDate today);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from Task t where t.id = ?1")
    java.util.Optional<Task> findLockedById(Long id);
    @org.springframework.data.jpa.repository.Query("select t.id from Task t where t.userId = ?1 and t.status = ?2 order by t.id")
    List<Long> findIdsByUserIdAndStatus(Long userId, PlanStatus status);
    List<Task> findByUserId(Long userId);
    List<Task> findByUserIdAndStatus(Long userId, PlanStatus status);

    @org.springframework.data.jpa.repository.Query("""
        select t from Task t where t.userId = :userId
          and (:start is null or t.endDate >= :start)
          and (:end is null or t.startDate <= :end)
          and (:status is null or (t.status not in (:active, :notStarted) and t.status = :status)
            or (:status = :active and t.status in (:active, :notStarted) and t.startDate <= :today and t.endDate >= :today)
            or (:status = :notStarted and t.status in (:active, :notStarted) and t.startDate > :today)
            or (:status = :expired and t.status in (:active, :notStarted) and t.endDate < :today))
        order by t.updatedAt desc, t.id desc
        """)
    List<Task> findFiltered(Long userId, PlanStatus status, java.time.LocalDate start, java.time.LocalDate end,
                            java.time.LocalDate today, PlanStatus active, PlanStatus notStarted, PlanStatus expired);
}
