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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plain unit tests (no Spring context) for {@link ProjectGoalDTO#from(ProjectGoal)}: the scalar goal state
 * maps across. The polymorphic property collection is intentionally not part of the DTO.
 */
class ProjectGoalDtoMappingTest {

    @Test
    @DisplayName("ProjectGoalDTO.from carries id + name + description")
    void projectGoalDtoFrom() {
        ProjectGoal goal = new ProjectGoal();
        goal.setId(3L);
        goal.setName("Cool it");
        goal.setDescription("keep temperature under 40C");

        ProjectGoalDTO dto = ProjectGoalDTO.from(goal);
        assertEquals(3L, dto.id());
        assertEquals("Cool it", dto.name());
        assertEquals("keep temperature under 40C", dto.description());
    }
}
