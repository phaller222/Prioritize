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

/**
 * Request body for creating or patching a {@link de.hallerweb.enterprise.prioritize.model.scheduling.TaskSchedule}.
 * The target project comes from the path, never the body. All fields are boxed/nullable so the same
 * record serves a PATCH (only non-null fields are applied); {@code createSchedule} enforces the ones
 * it requires. {@code nextFireAt}/{@code lastFiredAt} are runtime-managed and deliberately absent — a
 * client sets the cadence, not the individual fire times.
 *
 * @author peter haller
 */
public record TaskScheduleRequest(String name,
                                  String taskName,
                                  String taskDescription,
                                  Integer taskPriority,
                                  String cronExpression,
                                  String zoneId,
                                  Boolean enabled) {
}
