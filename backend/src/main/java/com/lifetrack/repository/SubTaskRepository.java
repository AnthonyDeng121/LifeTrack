package com.lifetrack.repository;

import com.lifetrack.entity.SubTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

@Repository
public interface SubTaskRepository extends JpaRepository<SubTask, Long> {
    @Query("select s.taskId from SubTask s where s.id = ?1")
    java.util.Optional<Long> findTaskIdById(Long id);
    List<SubTask> findByTaskIdInOrderByTaskIdAscOrderNumAsc(java.util.Collection<Long> ids);
    interface PlanCount { Long getTaskId(); long getStepCount(); }
    @Query("select s.taskId as taskId, count(s) as stepCount from SubTask s where s.taskId in ?1 group by s.taskId")
    List<PlanCount> countByPlans(java.util.Collection<Long> ids);
    long countByTaskId(Long taskId);
    List<SubTask> findByTaskIdOrderByOrderNumAsc(Long taskId);

    @Modifying
    @Query("delete from SubTask s where s.taskId = ?1")
    void deleteByTaskId(Long taskId);
}
