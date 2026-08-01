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

import de.hallerweb.enterprise.prioritize.model.project.goal.ProjectGoal;

/**
 * Flat, transport-safe view of a {@link ProjectGoal}. Carries the goal's scalar state (id, name,
 * description) only.
 * <p>
 * The polymorphic {@code properties} collection is deliberately kept OUT of this stable payload: its
 * subtypes are an evolving shape and one of them ({@code ProjectGoalPropertyDocument}) embeds a whole
 * {@code Document}, which would drag the document/user graph onto the wire. This mirrors the same
 * decision taken for the skill property collections. Progress is not carried here — it is derived and
 * exposed separately via the project-progress endpoint.
 *
 * @author peter haller
 */
public record ProjectGoalDTO(Long id,
                             String name,
                             String description) {

    /** Maps an entity to its DTO. */
    public static ProjectGoalDTO from(ProjectGoal goal) {
        return new ProjectGoalDTO(
                goal.getId(),
                goal.getName(),
                goal.getDescription());
    }
}
