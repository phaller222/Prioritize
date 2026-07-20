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

package de.hallerweb.enterprise.prioritize.model.scheduling;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A recurring task generator: on its cron cadence it creates a {@link de.hallerweb.enterprise.prioritize.model.project.Task}
 * from a fixed template on the target {@link Project}'s blackboard. This is the <b>neutral, generic
 * recurring-task primitive</b> of the platform core — vertical semantics (maintenance plans with
 * meter triggers, criticality, work-order types, …) belong to a product layer that builds on top,
 * not here.
 * <p>
 * <b>Recurrence</b> is a Spring {@link org.springframework.scheduling.support.CronExpression} in
 * {@link #zoneId} (or the server zone when blank). {@link #nextFireAt} is the pre-computed next due
 * time, <em>normalized to the server zone</em> so a single wall-clock comparison in the poller works
 * across schedules with different zones; it is {@code null} once the cron has no further occurrence.
 * {@link #lastFiredAt} is audit only.
 *
 * @author peter haller
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true) // id-based (via PObject); all-field hashing would walk the lazy project
public class TaskSchedule extends PObject {

    /** Human-readable name of the schedule itself (not of the tasks it produces). */
    private String name;

    /** The project whose blackboard receives the generated tasks. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private Project project;

    // ---- task template (what each generated task looks like) ---------------------------------

    /** Name given to every generated task. */
    private String taskName;

    /** Description given to every generated task. */
    private String taskDescription;

    /** Priority given to every generated task. */
    private int taskPriority;

    // ---- recurrence (cron only for now) ------------------------------------------------------

    /** Spring cron expression driving the cadence (6 fields: second minute hour day month weekday). */
    private String cronExpression;

    /** IANA zone id the cron is evaluated in (e.g. {@code Europe/Berlin}); server zone when blank. */
    private String zoneId;

    /** Whether this schedule is active. Disabled schedules never fire and are skipped by the poller. */
    private boolean enabled;

    /** Pre-computed next due time, normalized to the server zone; {@code null} when the cron is exhausted. */
    private LocalDateTime nextFireAt;

    /** When this schedule last produced a task; {@code null} until the first fire. Audit only. */
    private LocalDateTime lastFiredAt;
}
