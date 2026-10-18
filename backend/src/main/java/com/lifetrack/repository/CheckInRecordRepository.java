package com.lifetrack.repository;

import com.lifetrack.entity.CheckInRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CheckInRecordRepository extends JpaRepository<CheckInRecord, Long> {
    java.util.Optional<CheckInRecord> findByUserIdAndTaskIdAndCheckInDate(Long userId, Long taskId, LocalDate date);
    boolean existsByUserIdAndTaskIdAndCheckInDate(Long userId, Long taskId, LocalDate checkInDate);

    List<CheckInRecord> findByUserIdAndTaskIdAndCheckInDateBetweenOrderByCheckInDateDescCreatedAtDesc(
            Long userId, Long taskId, LocalDate startDate, LocalDate endDate);

    List<CheckInRecord> findByUserIdAndCheckInDateBetweenOrderByCheckInDateDescCreatedAtDesc(
            Long userId, LocalDate startDate, LocalDate endDate);

    List<CheckInRecord> findByUserIdAndTaskIdOrderByCheckInDateAsc(Long userId, Long taskId);

    void deleteByTaskId(Long taskId);
}
