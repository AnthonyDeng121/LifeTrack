package com.lifetrack.service;

import com.lifetrack.common.UserContext;
import com.lifetrack.dto.SubTaskCreateRequest;
import com.lifetrack.entity.*;
import com.lifetrack.exception.BusinessException;
import com.lifetrack.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class SubTaskManagementTest {
    @Mock TaskRepository tasks;
    @Mock SubTaskRepository steps;
    @Mock ActionLogRepository logs;
    @Mock AIService ai;
    @Mock CheckInRecordRepository checkIns;
    @Mock DeferredLearningRecordRepository deferred;
    @InjectMocks TaskService service;
    Task task;
    @BeforeEach void setup() {
        UserContext.setUserId(7L);
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        task = Task.builder().id(1L).userId(7L).status(PlanStatus.IN_PROGRESS)
                .startDate(today).endDate(today.plusDays(6)).build();
    }
    @AfterEach void clear() { UserContext.clear(); }
    SubTask step(long id, String weight, String progress) {
        return SubTask.builder().id(id).taskId(1L).weight(new BigDecimal(weight))
                .currentProgress(new BigDecimal(progress)).orderNum((int) id).build();
    }
    SubTaskCreateRequest request() {
        var r = new SubTaskCreateRequest(); r.setContent("练习新步骤"); r.setWeight(new BigDecimal("0.30")); return r;
    }
    @Test void additionRedistributesWeightsAndPreservesExistingProgress() {
        var existing = new ArrayList<>(List.of(step(11, "0.60", "0.50"), step(12, "0.40", "0.25")));
        when(tasks.findLockedById(1L)).thenReturn(Optional.of(task));
        when(tasks.findById(1L)).thenReturn(Optional.of(task));
        when(steps.findByTaskIdOrderByOrderNumAsc(1L)).thenReturn(existing);
        when(steps.save(any())).thenAnswer(i -> { SubTask added = i.getArgument(0); existing.add(added); return added; });
        service.addSubTask(1L, request());
        assertEquals(3, existing.size());
        assertEquals(0, existing.stream().map(SubTask::getWeight).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal.ONE));
        assertEquals(new BigDecimal("0.50"), existing.get(0).getCurrentProgress());
        assertEquals(0, task.getTotalProgress().compareTo(new BigDecimal("0.28")));
    }
    @Test void additionRejectsUnownedPlan() {
        task.setUserId(99L);
        when(tasks.findLockedById(1L)).thenReturn(Optional.of(task));
        assertThrows(BusinessException.class, () -> service.addSubTask(1L, request()));
        verifyNoInteractions(steps);
    }
    @Test void additionRejectsBlankContentAndInvalidWeight() {
        when(tasks.findLockedById(1L)).thenReturn(Optional.of(task));
        var r = request(); r.setContent("  ");
        assertThrows(BusinessException.class, () -> service.addSubTask(1L, r));
        r.setContent("学习"); r.setWeight(new BigDecimal("1.1"));
        assertThrows(BusinessException.class, () -> service.addSubTask(1L, r));
        verifyNoInteractions(steps);
    }
    @Test void deletionRejectsLastStep() {
        SubTask only = step(11, "1", "0");
        when(steps.findById(11L)).thenReturn(Optional.of(only));
        when(tasks.findLockedById(1L)).thenReturn(Optional.of(task));
        when(steps.findByTaskIdOrderByOrderNumAsc(1L)).thenReturn(List.of(only));
        assertThrows(BusinessException.class, () -> service.deleteSubTask(11L));
        verify(steps, never()).delete(any());
    }
    @Test void deletionRedistributesEvenWhenRemainingWeightsWereZero() {
        var existing = new ArrayList<>(List.of(step(11, "1", "0"), step(12, "0", "0"), step(13, "0", "0")));
        when(steps.findById(11L)).thenReturn(Optional.of(existing.get(0)));
        when(tasks.findLockedById(1L)).thenReturn(Optional.of(task));
        when(tasks.findById(1L)).thenReturn(Optional.of(task));
        when(steps.findByTaskIdOrderByOrderNumAsc(1L)).thenReturn(existing);
        doAnswer(i -> { existing.remove(i.getArgument(0)); return null; }).when(steps).delete(any());
        service.deleteSubTask(11L);
        assertEquals(2, existing.size());
        assertTrue(existing.stream().allMatch(st -> st.getWeight().compareTo(new BigDecimal("0.5")) == 0));
    }
}
