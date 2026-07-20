/*
 * Copyright 2026 Peter Michael Haller and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.hallerweb.enterprise.prioritize.service.scheduling;

import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.scheduling.TaskSchedule;
import de.hallerweb.enterprise.prioritize.repository.scheduling.TaskScheduleRepository;
import de.hallerweb.enterprise.prioritize.service.project.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link TaskScheduleService} — the zone-aware cron computation and the
 * due-firing orchestration (task creation via the trusted path, timestamp advance, per-schedule
 * failure isolation). No Spring context, no database.
 *
 * @author peter haller
 */
class TaskScheduleServiceTest {

    private TaskScheduleRepository scheduleRepository;
    private TaskService taskService;
    private TaskScheduleService service;

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @BeforeEach
    void setUp() {
        scheduleRepository = mock(TaskScheduleRepository.class);
        taskService = mock(TaskService.class);
        service = new TaskScheduleService(scheduleRepository, taskService);
    }

    // ---- cron computation --------------------------------------------------------------------

    @Test
    @DisplayName("nextFireAfter returns the next daily occurrence strictly after 'from'")
    void nextFireAfter_dailyCron() {
        // 08:00 already passed on Jan 1 at 09:00 -> next is Jan 2 08:00
        LocalDateTime next = TaskScheduleService.nextFireAfter(
                "0 0 8 * * *", UTC, UTC, LocalDateTime.of(2026, 1, 1, 9, 0));
        assertEquals(LocalDateTime.of(2026, 1, 2, 8, 0), next);
    }

    @Test
    @DisplayName("nextFireAfter converts a zoned cron back into the storage zone")
    void nextFireAfter_zoneConversion() {
        // 08:00 Berlin (UTC+1 in January) == 07:00 UTC; from 00:00 UTC the next such instant is same day
        LocalDateTime next = TaskScheduleService.nextFireAfter(
                "0 0 8 * * *", BERLIN, UTC, LocalDateTime.of(2026, 1, 1, 0, 0));
        assertEquals(LocalDateTime.of(2026, 1, 1, 7, 0), next);
    }

    @Test
    @DisplayName("nextFireAfter rejects an invalid cron expression")
    void nextFireAfter_invalidCron() {
        assertThrows(IllegalArgumentException.class, () -> TaskScheduleService.nextFireAfter(
                "not a cron", UTC, UTC, LocalDateTime.of(2026, 1, 1, 0, 0)));
    }

    @Test
    @DisplayName("nextFireFor falls back to the server zone when the schedule zone is blank")
    void nextFireFor_blankZoneUsesServerDefault() {
        TaskSchedule schedule = TaskSchedule.builder()
                .cronExpression("0 0/30 * * * *") // every 30 minutes
                .zoneId("  ")
                .build();
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 10, 5);
        LocalDateTime expected = TaskScheduleService.nextFireAfter(
                "0 0/30 * * * *", ZoneId.systemDefault(), ZoneId.systemDefault(), from);
        assertEquals(expected, service.nextFireFor(schedule, from));
    }

    // ---- firing --------------------------------------------------------------------------------

    @Test
    @DisplayName("runDueSchedules creates a task from the template and advances the schedule")
    void runDueSchedules_firesAndAdvances() {
        Project project = mock(Project.class);
        when(project.getName()).thenReturn("Ops");
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);
        TaskSchedule schedule = TaskSchedule.builder()
                .name("Nightly report")
                .project(project)
                .taskName("Generate report")
                .taskDescription("auto")
                .taskPriority(3)
                .cronExpression("0 0/30 * * * *")
                .enabled(true)
                .nextFireAt(now.minusMinutes(1))
                .build();
        when(scheduleRepository.findByEnabledTrueAndNextFireAtLessThanEqual(now))
                .thenReturn(List.of(schedule));

        int fired = service.runDueSchedules(now);

        assertEquals(1, fired);
        verify(taskService).createScheduledTask(project, "Generate report", "auto", 3);
        assertEquals(now, schedule.getLastFiredAt());
        assertTrue(schedule.getNextFireAt().isAfter(now), "nextFireAt must advance past now");
        verify(scheduleRepository).save(schedule);
    }

    @Test
    @DisplayName("runDueSchedules isolates a failing schedule and still fires the others")
    void runDueSchedules_isolatesFailure() {
        Project bad = mock(Project.class);
        Project good = mock(Project.class);
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);
        TaskSchedule failing = TaskSchedule.builder().name("bad").project(bad)
                .taskName("x").cronExpression("0 0/30 * * * *").enabled(true)
                .nextFireAt(now.minusMinutes(1)).build();
        TaskSchedule ok = TaskSchedule.builder().name("good").project(good)
                .taskName("y").cronExpression("0 0/30 * * * *").enabled(true)
                .nextFireAt(now.minusMinutes(1)).build();
        when(scheduleRepository.findByEnabledTrueAndNextFireAtLessThanEqual(now))
                .thenReturn(List.of(failing, ok));
        when(taskService.createScheduledTask(eq(bad), any(), any(), anyInt()))
                .thenThrow(new RuntimeException("boom"));

        int fired = service.runDueSchedules(now);

        assertEquals(1, fired);
        // The failing schedule is not advanced (state check — both are id-less so equals can't tell
        // them apart, hence identity via a captor rather than verify(save(ok))).
        assertNull(failing.getLastFiredAt());
        assertEquals(now, ok.getLastFiredAt());
        ArgumentCaptor<TaskSchedule> saved = ArgumentCaptor.forClass(TaskSchedule.class);
        verify(scheduleRepository, times(1)).save(saved.capture());
        assertSame(ok, saved.getValue());
    }

    @Test
    @DisplayName("runDueSchedules does nothing when no schedule is due")
    void runDueSchedules_noneDue() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0);
        when(scheduleRepository.findByEnabledTrueAndNextFireAtLessThanEqual(now))
                .thenReturn(List.of());

        assertEquals(0, service.runDueSchedules(now));
        verifyNoInteractions(taskService);
    }
}
