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

import de.hallerweb.enterprise.prioritize.model.scheduling.TaskSchedule;
import de.hallerweb.enterprise.prioritize.repository.scheduling.TaskScheduleRepository;
import de.hallerweb.enterprise.prioritize.service.project.TaskService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.support.CronExpression;
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
}
