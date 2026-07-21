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

import de.hallerweb.enterprise.prioritize.dto.scheduling.TaskScheduleDTO;
import de.hallerweb.enterprise.prioritize.dto.scheduling.TaskScheduleRequest;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.scheduling.TaskSchedule;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.scheduling.TaskScheduleRepository;
import de.hallerweb.enterprise.prioritize.service.project.ProjectService;
import de.hallerweb.enterprise.prioritize.service.project.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link TaskScheduleService} — the zone-aware cron computation, the due-firing
 * orchestration (task creation via the trusted path, timestamp advance, per-schedule failure
 * isolation) and the admin CRUD (validation, membership delegation, nextFireAt bookkeeping). No
 * Spring context, no database.
 *
 * @author peter haller
 */
class TaskScheduleServiceTest {

    private TaskScheduleRepository scheduleRepository;
    private TaskService taskService;
    private ProjectService projectService;
    private TaskScheduleService service;

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private final PUser user = new PUser();

    @BeforeEach
    void setUp() {
        scheduleRepository = mock(TaskScheduleRepository.class);
        taskService = mock(TaskService.class);
        projectService = mock(ProjectService.class);
        service = new TaskScheduleService(scheduleRepository, taskService, projectService);
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

    // ---- CRUD ----------------------------------------------------------------------------------

    /** A project the mocked {@link ProjectService} resolves for id 5 (authorization is stubbed). */
    private Project stubProject() {
        Project project = mock(Project.class);
        when(projectService.findOrThrow(5L)).thenReturn(project);
        return project;
    }

    /** Makes {@code save} return its argument, as a real repository does for an existing entity. */
    private void stubSaveEcho() {
        when(scheduleRepository.save(any(TaskSchedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static TaskScheduleRequest fullRequest() {
        return new TaskScheduleRequest("Nightly", "Generate report", "auto", 3,
                "0 0 8 * * *", "Europe/Berlin", true);
    }

    @Test
    @DisplayName("createSchedule: manager only, persists the template and precomputes nextFireAt")
    void createSchedule_createsAndComputesNextFire() {
        Project project = stubProject();
        stubSaveEcho();

        TaskScheduleDTO dto = service.createSchedule(5L, fullRequest(), user);

        verify(projectService).requireManager(project, user);
        assertEquals("Nightly", dto.name());
        assertEquals("Generate report", dto.taskName());
        assertEquals(3, dto.taskPriority());
        assertEquals("Europe/Berlin", dto.zoneId());
        assertTrue(dto.enabled());
        assertTrue(dto.nextFireAt().isAfter(LocalDateTime.now()), "an enabled schedule must be armed");
    }

    @Test
    @DisplayName("createSchedule: a disabled schedule is not armed")
    void createSchedule_disabledIsNotArmed() {
        stubProject();
        stubSaveEcho();
        TaskScheduleRequest req = new TaskScheduleRequest(null, "Generate report", null, null,
                "0 0 8 * * *", null, false);

        TaskScheduleDTO dto = service.createSchedule(5L, req, user);

        assertFalse(dto.enabled());
        assertNull(dto.nextFireAt());
        assertEquals("Generate report", dto.name(), "a missing name falls back to the task name");
        assertEquals(0, dto.taskPriority());
    }

    @Test
    @DisplayName("createSchedule: rejects a missing task name, a bad cron and an unknown zone")
    void createSchedule_rejectsInvalidInput() {
        stubProject();

        assertThrows(IllegalArgumentException.class, () -> service.createSchedule(5L,
                new TaskScheduleRequest(null, "  ", null, null, "0 0 8 * * *", null, true), user));
        assertThrows(IllegalArgumentException.class, () -> service.createSchedule(5L,
                new TaskScheduleRequest(null, "x", null, null, null, null, true), user));
        assertThrows(IllegalArgumentException.class, () -> service.createSchedule(5L,
                new TaskScheduleRequest(null, "x", null, null, "not a cron", null, true), user));
        assertThrows(IllegalArgumentException.class, () -> service.createSchedule(5L,
                new TaskScheduleRequest(null, "x", null, null, "0 0 8 * * *", "Mars/Olympus", true), user));
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("createSchedule: propagates the manager check")
    void createSchedule_deniedForNonManager() {
        Project project = stubProject();
        doThrow(new AccessDeniedException("nope")).when(projectService).requireManager(project, user);

        assertThrows(AccessDeniedException.class, () -> service.createSchedule(5L, fullRequest(), user));
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("getSchedules: readable by any project member")
    void getSchedules_allowsMembers() {
        Project project = stubProject();
        when(scheduleRepository.findByProject_Id(5L)).thenReturn(List.of(
                TaskSchedule.builder().name("a").project(project).taskName("x")
                        .cronExpression("0 0 8 * * *").enabled(true).build()));

        List<TaskScheduleDTO> schedules = service.getSchedules(5L, user);

        verify(projectService).requireMemberOrManager(project, user);
        assertEquals(1, schedules.size());
        assertEquals("a", schedules.get(0).name());
    }

    @Test
    @DisplayName("getSchedule: unknown id is a 404-mapped NoSuchElementException")
    void getSchedule_notFound() {
        when(scheduleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.getSchedule(99L, user));
    }

    /** An existing, armed schedule as it would come back from the repository. */
    private TaskSchedule existingSchedule(Project project, LocalDateTime nextFireAt) {
        TaskSchedule schedule = TaskSchedule.builder()
                .name("Nightly").project(project)
                .taskName("Generate report").taskDescription("auto").taskPriority(3)
                .cronExpression("0 0 8 * * *").zoneId("Europe/Berlin").enabled(true)
                .nextFireAt(nextFireAt)
                .build();
        schedule.setId(42L);
        when(scheduleRepository.findById(42L)).thenReturn(Optional.of(schedule));
        return schedule;
    }

    @Test
    @DisplayName("updateSchedule: a new cron re-arms the schedule, a rename does not")
    void updateSchedule_recomputesOnlyOnCadenceChange() {
        Project project = mock(Project.class);
        LocalDateTime armedAt = LocalDateTime.now().plusDays(1).withNano(0);
        TaskSchedule schedule = existingSchedule(project, armedAt);
        stubSaveEcho();

        // rename only -> the pending fire time stays untouched
        TaskScheduleDTO renamed = service.updateSchedule(42L,
                new TaskScheduleRequest("Renamed", null, null, null, null, null, null), user);
        verify(projectService).requireManager(project, user);
        assertEquals("Renamed", renamed.name());
        assertEquals(armedAt, renamed.nextFireAt());

        // new cron -> re-armed
        TaskScheduleDTO recron = service.updateSchedule(42L,
                new TaskScheduleRequest(null, null, null, null, "0 0/5 * * * *", null, null), user);
        assertTrue(recron.nextFireAt().isBefore(armedAt), "a 5-minute cron must fire before tomorrow");
        assertEquals("0 0/5 * * * *", schedule.getCronExpression());
    }

    @Test
    @DisplayName("updateSchedule: re-enabling a disabled schedule re-arms it")
    void updateSchedule_reEnableArms() {
        Project project = mock(Project.class);
        TaskSchedule schedule = existingSchedule(project, null);
        schedule.setEnabled(false);
        stubSaveEcho();

        TaskScheduleDTO dto = service.updateSchedule(42L,
                new TaskScheduleRequest(null, null, null, null, null, null, true), user);

        assertTrue(dto.enabled());
        assertTrue(dto.nextFireAt().isAfter(LocalDateTime.now()));
    }

    @Test
    @DisplayName("updateSchedule: rejects a blank task name and an invalid cron")
    void updateSchedule_rejectsInvalidPatch() {
        existingSchedule(mock(Project.class), LocalDateTime.now().plusDays(1));

        assertThrows(IllegalArgumentException.class, () -> service.updateSchedule(42L,
                new TaskScheduleRequest(null, "  ", null, null, null, null, null), user));
        assertThrows(IllegalArgumentException.class, () -> service.updateSchedule(42L,
                new TaskScheduleRequest(null, null, null, null, "not a cron", null, null), user));
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteSchedule: manager only")
    void deleteSchedule_managerOnly() {
        Project project = mock(Project.class);
        TaskSchedule schedule = existingSchedule(project, LocalDateTime.now().plusDays(1));

        service.deleteSchedule(42L, user);

        verify(projectService).requireManager(project, user);
        verify(scheduleRepository).delete(schedule);
    }
}
