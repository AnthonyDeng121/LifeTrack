package com.lifetrack.repository;

import com.lifetrack.entity.SubTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

@Repository
public interface SubTaskRepository extends JpaRepository<SubTask, Long> {
    List<SubTask> findByTaskIdOrderByOrderNumAsc(Long taskId);

    @Modifying
    @Query("delete from SubTask s where s.taskId = ?1")
    void deleteByTaskId(Long taskId);
}
