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

package de.hallerweb.enterprise.prioritize.dto.process;

import java.time.Instant;

/**
 * Flat view of one running or finished BPMN process instance, as far as the platform cares about it:
 * what it was started from, what it belongs to, and whether it is still going.
 * <p>
 * There is deliberately no entity behind this. The engine owns its instances — mirroring them into a
 * table would create a second source of truth that drifts the moment a process ends outside our reach.
 * What the platform stores is the pointer in the other direction
 * ({@link de.hallerweb.enterprise.prioritize.model.project.Task#getProcessInstanceId()}).
 *
 * @param id           the engine's process instance id
 * @param processKey   the BPMN process key that was started
 * @param definitionId the registry id of the {@link de.hallerweb.enterprise.prioritize.model.process.ProcessDefinition}
 * @param businessKey  what this instance is about, e.g. {@code task:42} — see {@code ProcessInstanceService}
 * @param projectId    the project the instance belongs to
 * @param taskId       the task it belongs to, or {@code null} for a project-level instance
 * @param running      {@code false} once the instance has ended (a process without a wait state ends
 *                     during the very call that started it)
 * @param startedAt    when the engine started it
 * @param startedBy    username of whoever started it
 *
 * @author peter haller
 */
public record ProcessInstanceDTO(String id,
                                 String processKey,
                                 Long definitionId,
                                 String businessKey,
                                 Long projectId,
                                 Long taskId,
                                 boolean running,
                                 Instant startedAt,
                                 String startedBy) {
}
