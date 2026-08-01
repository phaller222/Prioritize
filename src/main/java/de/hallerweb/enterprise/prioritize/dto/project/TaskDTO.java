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

package de.hallerweb.enterprise.prioritize.dto.project;

import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.project.TaskStatus;

/**
 * Flat, transport-safe view of a {@link Task}. Carries the scalar task state plus the assignee and goal
 * by id, but never the lazy relations (assignee/goal entities, resources, documents, skills, blackboard,
 * time spans) — so serializing a task never triggers a {@code LazyInitializationException} nor drags the
 * polymorphic {@code PActor}/{@code ProjectGoal} graph onto the wire. {@code tracking} mirrors
 * {@link Task#isTracking()}. The assignee/goal ids read off the lazy proxies without initializing them.
 *
 * @author peter haller
 */
public record TaskDTO(Long id,
                      String name,
                      String description,
                      int priority,
                      TaskStatus taskStatus,
                      Long assigneeId,
                      Long goalId,
                      String processInstanceId,
                      boolean tracking) {

    /** Maps an entity to its DTO. Reads the lazy assignee/goal ids only (safe under open-in-view). */
    public static TaskDTO from(Task task) {
        return new TaskDTO(
                task.getId(),
                task.getName(),
                task.getDescription(),
                task.getPriority(),
                task.getTaskStatus(),
                task.getAssignee() != null ? task.getAssignee().getId() : null,
                task.getGoal() != null ? task.getGoal().getId() : null,
                task.getProcessInstanceId(),
                task.isTracking());
    }
}
