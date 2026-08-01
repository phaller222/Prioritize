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

import de.hallerweb.enterprise.prioritize.model.project.Project;

import java.time.LocalDate;
import java.util.List;

/**
 * Flat, transport-safe view of a {@link Project}. Carries the scalar project state plus the manager, the
 * members and the blackboard by id, but never the lazy relations (manager/member entities, blackboard with
 * its tasks, resources, documents, skills, goals) — so serializing a project never triggers a
 * {@code LazyInitializationException} nor drags the {@code PUser}/{@code Blackboard} graph onto the wire.
 * The manager/blackboard ids read off the (eager) relations; {@code memberIds} maps the eager member set.
 *
 * @author peter haller
 */
public record ProjectDTO(Long id,
                         String name,
                         String description,
                         int priority,
                         LocalDate beginDate,
                         LocalDate dueDate,
                         int maxManDays,
                         Long managerId,
                         List<Long> memberIds,
                         Long blackboardId) {

    /** Maps an entity to its DTO. Reads relation ids only (members are eager; manager/blackboard by id). */
    public static ProjectDTO from(Project project) {
        return new ProjectDTO(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getPriority(),
                project.getBeginDate(),
                project.getDueDate(),
                project.getMaxManDays(),
                project.getManager() != null ? project.getManager().getId() : null,
                project.getMembers() != null
                        ? project.getMembers().stream().map(m -> m.getId()).toList()
                        : List.of(),
                project.getBlackboard() != null ? project.getBlackboard().getId() : null);
    }
}
