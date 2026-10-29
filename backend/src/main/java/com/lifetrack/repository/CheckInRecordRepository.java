package com.lifetrack.repository;

import com.lifetrack.entity.CheckInRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CheckInRecordRepository extends JpaRepository<CheckInRecord, Long> {
    long countByUserIdAndTaskIdAndCheckInDateBetween(Long userId, Long taskId, LocalDate start, LocalDate end);
    @org.springframework.data.jpa.repository.Query("select r.checkInDate from CheckInRecord r where r.userId=?1 and r.taskId=?2 order by r.checkInDate")
    List<LocalDate> findPlanDates(Long userId, Long taskId);
    @org.springframework.data.jpa.repository.Query("select r from CheckInRecord r where r.userId = :userId and (:taskId is null or r.taskId = :taskId) and r.checkInDate between :start and :end")
    org.springframework.data.domain.Page<CheckInRecord> findHistoryPage(Long userId, Long taskId, LocalDate start, LocalDate end, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("select r.id from CheckInRecord r where r.userId=?1 and r.taskId=?2 and (r.checkInDate < ?3 or r.checkInDate > ?4) order by r.id")
    List<Long> findOutsideIds(Long userId, Long taskId, LocalDate start, LocalDate end);

    @org.springframework.data.jpa.repository.Query("select distinct r.checkInDate from CheckInRecord r join Task t on t.id=r.taskId and t.userId=r.userId where r.userId=?1 and t.status <> ?2 and r.checkInDate <= ?3 order by r.checkInDate")
    List<LocalDate> findDashboardDates(Long userId, com.lifetrack.entity.PlanStatus excluded, LocalDate today);
    List<CheckInRecord> findByUserIdAndTaskIdIn(Long userId, java.util.Collection<Long> taskIds);
    java.util.Optional<CheckInRecord> findByUserIdAndTaskIdAndCheckInDate(Long userId, Long taskId, LocalDate date);
    boolean existsByUserIdAndTaskIdAndCheckInDate(Long userId, Long taskId, LocalDate checkInDate);

    List<CheckInRecord> findByUserIdAndTaskIdAndCheckInDateBetweenOrderByCheckInDateDescCreatedAtDesc(
            Long userId, Long taskId, LocalDate startDate, LocalDate endDate);

    List<CheckInRecord> findByUserIdAndCheckInDateBetweenOrderByCheckInDateDescCreatedAtDesc(
            Long userId, LocalDate startDate, LocalDate endDate);

    List<CheckInRecord> findByUserIdAndTaskIdOrderByCheckInDateAsc(Long userId, Long taskId);

    void deleteByTaskId(Long taskId);
}
