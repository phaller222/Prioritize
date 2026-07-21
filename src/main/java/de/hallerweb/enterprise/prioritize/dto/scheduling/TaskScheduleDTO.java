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

package de.hallerweb.enterprise.prioritize.dto.scheduling;

import de.hallerweb.enterprise.prioritize.model.scheduling.TaskSchedule;
import java.time.LocalDateTime;

/**
 * Flat, transport-safe view of a {@link TaskSchedule}. Carries only the target project's id, never the
 * lazy {@code Project} itself, so serializing a schedule never triggers a
 * {@code LazyInitializationException} or drags the whole project graph onto the wire. Mapped inside a
 * service transaction.
 *
 * @author peter haller
 */
public record TaskScheduleDTO(Long id,
                              Long projectId,
                              String name,
                              String taskName,
                              String taskDescription,
                              int taskPriority,
                              String cronExpression,
                              String zoneId,
                              boolean enabled,
                              LocalDateTime nextFireAt,
                              LocalDateTime lastFiredAt) {

    /** Maps an entity to its DTO. Call within a transaction ({@code project.getId()} is lazy). */
    public static TaskScheduleDTO from(TaskSchedule schedule) {
        return new TaskScheduleDTO(
                schedule.getId(),
                schedule.getProject() != null ? schedule.getProject().getId() : null,
                schedule.getName(),
                schedule.getTaskName(),
                schedule.getTaskDescription(),
                schedule.getTaskPriority(),
                schedule.getCronExpression(),
                schedule.getZoneId(),
                schedule.isEnabled(),
                schedule.getNextFireAt(),
                schedule.getLastFiredAt());
    }
}
