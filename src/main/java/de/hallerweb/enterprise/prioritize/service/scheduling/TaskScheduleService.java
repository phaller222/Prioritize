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
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates recurring {@link TaskSchedule}s: computes their cron cadence and, when due, generates
 * tasks from their template via {@link TaskService#createScheduledTask}.
 * <p>
 * <b>Firing</b> is done by {@link #runDueSchedules(LocalDateTime)}, a plain method that a poller (a
 * separate slice) calls on a fixed cadence — this class itself does no background scheduling. For
 * each due schedule it creates the task through the trusted, user-less path, records
 * {@code lastFiredAt} and advances {@code nextFireAt} to the next cron occurrence. A failure on one
 * schedule is logged and isolated so the rest of the batch still fires.
 * <p>
 * <b>Zones:</b> a schedule's cron is evaluated in its own {@link TaskSchedule#getZoneId() zone} (or
 * the server zone when blank), but {@code nextFireAt} is stored <em>normalized to the server zone</em>
 * so the poller can compare every schedule against a single {@code LocalDateTime.now()} regardless of
 * per-schedule zones. See {@link #nextFireAfter}.
 * <p>
 * <b>Administration:</b> create/read/update/delete over a project's schedules. A schedule is durable
 * project configuration that keeps producing tasks unattended, so mutations are <b>manager only</b>
 * (like project goals and the project's team/resource/document management), while reading is open to
 * every member. Authorization is delegated to {@link ProjectService} — the same membership model as
 * the rest of the project API, with no admin bypass.
 *
 * @author peter haller
 */
@Service
@RequiredArgsConstructor
@Transactional
@Log4j2
public class TaskScheduleService {

    private final TaskScheduleRepository scheduleRepository;
    private final TaskService taskService;
    private final ProjectService projectService;

    // ---- firing ------------------------------------------------------------------------------

    /**
     * Fires every enabled schedule that is due at or before {@code now} (server-zone wall clock):
     * generates a task from its template, stamps {@code lastFiredAt} and advances {@code nextFireAt}.
     * Failures are isolated per schedule.
     *
     * @param now the current server-zone timestamp
     * @return the number of schedules that fired successfully
     */
    public int runDueSchedules(LocalDateTime now) {
        List<TaskSchedule> due = scheduleRepository.findByEnabledTrueAndNextFireAtLessThanEqual(now);
        int fired = 0;
        for (TaskSchedule schedule : due) {
            try {
                taskService.createScheduledTask(schedule.getProject(), schedule.getTaskName(),
                        schedule.getTaskDescription(), schedule.getTaskPriority());
                schedule.setLastFiredAt(now);
                schedule.setNextFireAt(nextFireFor(schedule, now));
                scheduleRepository.save(schedule);
                fired++;
            } catch (Exception ex) {
                // Isolate a bad schedule (missing project, bad cron, …) so the rest of the batch fires.
                log.error("Task schedule {} ('{}') failed to fire; skipping it this run.",
                        schedule.getId(), schedule.getName(), ex);
            }
        }
        if (fired > 0) {
            log.info("Fired {} of {} due task schedule(s).", fired, due.size());
        }
        return fired;
    }

    /**
     * The next fire time for a schedule after {@code fromServerLocal}, normalized to the server zone.
     * The schedule's own zone (or the server zone when blank) drives the cron's wall-clock meaning.
     *
     * @return the next occurrence, or {@code null} if the cron has none
     */
    public LocalDateTime nextFireFor(TaskSchedule schedule, LocalDateTime fromServerLocal) {
        ZoneId serverZone = ZoneId.systemDefault();
        ZoneId cronZone = (schedule.getZoneId() == null || schedule.getZoneId().isBlank())
                ? serverZone : ZoneId.of(schedule.getZoneId());
        return nextFireAfter(schedule.getCronExpression(), cronZone, serverZone, fromServerLocal);
    }

    /**
     * Pure cron computation: the next occurrence of {@code cron} strictly after {@code from}, where
     * {@code from} and the result are wall-clock times in {@code storageZone} while the cron itself is
     * interpreted in {@code cronZone}. Keeping the two zones explicit makes the result deterministic
     * (and unit-testable) independent of the running JVM's default zone.
     *
     * @return the next occurrence in {@code storageZone}, or {@code null} if the cron has none
     * @throws IllegalArgumentException if {@code cron} is not a valid Spring cron expression
     */
    static LocalDateTime nextFireAfter(String cron, ZoneId cronZone, ZoneId storageZone,
                                       LocalDateTime from) {
        ZonedDateTime fromInCronZone = from.atZone(storageZone).withZoneSameInstant(cronZone);
        ZonedDateTime next = CronExpression.parse(cron).next(fromInCronZone);
        return next == null ? null : next.withZoneSameInstant(storageZone).toLocalDateTime();
    }

    // ---- administration (CRUD) ---------------------------------------------------------------

    /**
     * Creates a schedule on a project. Manager only. An enabled schedule gets its {@code nextFireAt}
     * computed right away, so it starts firing without waiting for an update.
     *
     * @throws NoSuchElementException   if the project does not exist
     * @throws AccessDeniedException    if the user is not the project's manager
     * @throws IllegalArgumentException if the request is incomplete or the cron/zone is invalid
     */
    public TaskScheduleDTO createSchedule(Long projectId, TaskScheduleRequest req, PUser user) {
        Project project = projectService.findOrThrow(projectId);
        projectService.requireManager(project, user);

        String taskName = req == null ? null : req.taskName();
        if (taskName == null || taskName.isBlank()) {
            throw new IllegalArgumentException("taskName is required.");
        }
        if (req.cronExpression() == null || req.cronExpression().isBlank()) {
            throw new IllegalArgumentException("cronExpression is required.");
        }
        validateCron(req.cronExpression());
        validateZone(req.zoneId());

        TaskSchedule schedule = TaskSchedule.builder()
                .project(project)
                .name(req.name() == null || req.name().isBlank() ? taskName.trim() : req.name().trim())
                .taskName(taskName.trim())
                .taskDescription(req.taskDescription())
                .taskPriority(req.taskPriority() == null ? 0 : req.taskPriority())
                .cronExpression(req.cronExpression().trim())
                .zoneId(req.zoneId())
                .enabled(req.enabled() == null || req.enabled())
                .build();
        if (schedule.isEnabled()) {
            schedule.setNextFireAt(nextFireFor(schedule, LocalDateTime.now()));
        }

        TaskSchedule saved = scheduleRepository.save(schedule);
        log.debug("Task schedule {} ('{}') created on project {} by '{}'; next fire {}.",
                saved.getId(), saved.getName(), projectId, user.getUsername(), saved.getNextFireAt());
        return TaskScheduleDTO.from(saved);
    }

    /** Lists a project's schedules (enabled or not). Any member or the manager may read. */
    @Transactional(readOnly = true)
    public List<TaskScheduleDTO> getSchedules(Long projectId, PUser user) {
        Project project = projectService.findOrThrow(projectId);
        projectService.requireMemberOrManager(project, user);
        return scheduleRepository.findByProject_Id(projectId).stream()
                .map(TaskScheduleDTO::from)
                .toList();
    }

    /** Reads a single schedule. Any member or the manager of its project may read. */
    @Transactional(readOnly = true)
    public TaskScheduleDTO getSchedule(Long id, PUser user) {
        TaskSchedule schedule = requireSchedule(id);
        projectService.requireMemberOrManager(projectOf(schedule), user);
        return TaskScheduleDTO.from(schedule);
    }

    /**
     * Applies a partial update (only non-null request fields). Manager only. {@code nextFireAt} is
     * recomputed when the cadence changed or the schedule was (re-)enabled — an unrelated edit (a
     * rename, a new task description) deliberately leaves a pending fire time untouched so it cannot
     * be pushed past its due moment.
     */
    public TaskScheduleDTO updateSchedule(Long id, TaskScheduleRequest patch, PUser user) {
        TaskSchedule schedule = requireSchedule(id);
        projectService.requireManager(projectOf(schedule), user);
        if (patch == null) {
            return TaskScheduleDTO.from(schedule);
        }

        String previousCron = schedule.getCronExpression();
        String previousZone = schedule.getZoneId();
        boolean wasEnabled = schedule.isEnabled();

        if (patch.name() != null) {
            if (patch.name().isBlank()) {
                throw new IllegalArgumentException("name must not be blank.");
            }
            schedule.setName(patch.name().trim());
        }
        if (patch.taskName() != null) {
            if (patch.taskName().isBlank()) {
                throw new IllegalArgumentException("taskName must not be blank.");
            }
            schedule.setTaskName(patch.taskName().trim());
        }
        if (patch.taskDescription() != null) {
            schedule.setTaskDescription(patch.taskDescription());
        }
        if (patch.taskPriority() != null) {
            schedule.setTaskPriority(patch.taskPriority());
        }
        if (patch.cronExpression() != null) {
            if (patch.cronExpression().isBlank()) {
                throw new IllegalArgumentException("cronExpression must not be blank.");
            }
            validateCron(patch.cronExpression());
            schedule.setCronExpression(patch.cronExpression().trim());
        }
        if (patch.zoneId() != null) {
            validateZone(patch.zoneId());
            schedule.setZoneId(patch.zoneId());
        }
        if (patch.enabled() != null) {
            schedule.setEnabled(patch.enabled());
        }

        boolean cadenceChanged = !Objects.equals(previousCron, schedule.getCronExpression())
                || !Objects.equals(previousZone, schedule.getZoneId());
        if (schedule.isEnabled()
                && (cadenceChanged || !wasEnabled || schedule.getNextFireAt() == null)) {
            schedule.setNextFireAt(nextFireFor(schedule, LocalDateTime.now()));
        }

        return TaskScheduleDTO.from(scheduleRepository.save(schedule));
    }

    /** Deletes a schedule. Manager only. Already generated tasks are not touched. */
    public void deleteSchedule(Long id, PUser user) {
        TaskSchedule schedule = requireSchedule(id);
        projectService.requireManager(projectOf(schedule), user);
        scheduleRepository.delete(schedule);
        log.debug("Task schedule {} ('{}') deleted by '{}'.",
                id, schedule.getName(), user.getUsername());
    }

    // ---- helpers -----------------------------------------------------------------------------

    private TaskSchedule requireSchedule(Long id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Task schedule not found"));
    }

    /** The schedule's target project; a schedule without one cannot be authorized against. */
    private static Project projectOf(TaskSchedule schedule) {
        Project project = schedule.getProject();
        if (project == null) {
            throw new IllegalStateException("Task schedule is not attached to a project.");
        }
        return project;
    }

    /** @throws IllegalArgumentException if the cron is not a valid Spring cron expression */
    private static void validateCron(String cron) {
        CronExpression.parse(cron.trim()); // throws IllegalArgumentException -> 400
    }

    /**
     * Accepts a blank/absent zone (meaning "server zone").
     *
     * @throws IllegalArgumentException if the zone id is unknown — {@code ZoneId.of} signals that with
     *                                  a {@link DateTimeException}, which would otherwise surface as a 500
     */
    private static void validateZone(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return;
        }
        try {
            ZoneId.of(zoneId);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Unknown zoneId: " + zoneId, ex);
        }
    }
}
