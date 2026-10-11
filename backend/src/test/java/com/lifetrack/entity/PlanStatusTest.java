package com.lifetrack.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanStatusTest {

    @Test
    void shouldAllowOnlyDefinedStatusTransitions() {
        assertTrue(PlanStatus.NOT_STARTED.canTransitionTo(PlanStatus.IN_PROGRESS));
        assertTrue(PlanStatus.NOT_STARTED.canTransitionTo(PlanStatus.CANCELLED));
        assertTrue(PlanStatus.IN_PROGRESS.canTransitionTo(PlanStatus.COMPLETED));
        assertFalse(PlanStatus.COMPLETED.canTransitionTo(PlanStatus.IN_PROGRESS));
        assertFalse(PlanStatus.CANCELLED.canTransitionTo(PlanStatus.IN_PROGRESS));
        assertFalse(PlanStatus.EXPIRED.canTransitionTo(PlanStatus.IN_PROGRESS));
    }

    @Test
    void shouldKeepLegacyDatabaseCodesCompatible() {
        assertEquals(PlanStatus.IN_PROGRESS, PlanStatus.fromCode(0));
        assertEquals(PlanStatus.COMPLETED, PlanStatus.fromCode(1));
    }

    @Test
    void shouldParseEnglishNameAndChineseDescription() {
        assertEquals(PlanStatus.NOT_STARTED, PlanStatus.fromValue("NOT_STARTED"));
        assertEquals(PlanStatus.CANCELLED, PlanStatus.fromValue("已取消"));
    }
}
