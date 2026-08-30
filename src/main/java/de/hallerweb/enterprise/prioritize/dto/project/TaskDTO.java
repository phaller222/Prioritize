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
import de.hallerweb.enterprise.prioritize.model.security.PUser;

/**
 * Flat, transport-safe view of a {@link Task}. Carries the scalar task state plus the assignee and goal
 * by id, but never the lazy relations (assignee/goal entities, resources, documents, skills, blackboard,
 * time spans) — so serializing a task never triggers a {@code LazyInitializationException} nor drags the
 * polymorphic {@code PActor}/{@code ProjectGoal} graph onto the wire. {@code trackingForMe} mirrors
 * {@link Task#isTrackingFor(PUser)} for the viewing user — clocks are per person, so a task-wide
 * "is it tracking" would not say whose. The assignee/goal ids read off the lazy proxies without
 * initializing them.
 * <p>
 * {@code runningCount} is the other half of that answer: how many people are on the task right now,
 * the viewer included. Without it a task list could show "my clock is running" but not "two
 * colleagues are on this one" without one extra call per row.
 * <p>
 * {@code equipmentRunningCount} counts the machines clocked in, and is a separate number on purpose:
 * people and equipment are never added together. A task can sit at zero people and two devices —
 * the crew went home, the dryer keeps running — which is exactly the state a single count would hide.
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
                      boolean trackingForMe,
                      int runningCount,
                      int equipmentRunningCount) {

    /**
     * Maps an entity to its DTO for a given viewer. Reads the lazy assignee/goal ids only (safe under
     * open-in-view). The viewer decides {@code trackingForMe}; a colleague looking at the same task
     * sees their own clock, not this one's.
     */
    public static TaskDTO from(Task task, PUser viewer) {
        return new TaskDTO(
                task.getId(),
                task.getName(),
                task.getDescription(),
                task.getPriority(),
                task.getTaskStatus(),
                task.getAssignee() != null ? task.getAssignee().getId() : null,
                task.getGoal() != null ? task.getGoal().getId() : null,
                task.getProcessInstanceId(),
                task.isTrackingFor(viewer),
                task.getRunningCount(),
                task.getEquipmentRunningCount());
    }
}
