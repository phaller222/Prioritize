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

import de.hallerweb.enterprise.prioritize.model.project.Blackboard;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests (no Spring context) for {@link ProjectDTO#from(Project)}: the scalar project state maps
 * across, the manager and the blackboard flatten to their ids, the member set flattens to a list of ids,
 * and every relation is null-safe (a bare {@code new Project()} leaves the {@code @Builder.Default}
 * collections null, which the mapping must tolerate).
 */
class ProjectDtoMappingTest {

    @Test
    @DisplayName("ProjectDTO.from carries scalars + manager/member/blackboard ids")
    void projectDtoFrom() {
        PUser manager = new PUser();
        manager.setId(5L);
        PUser member = new PUser();
        member.setId(6L);
        Set<PUser> members = new HashSet<>();
        members.add(member);
        Blackboard blackboard = new Blackboard();
        blackboard.setId(8L);

        Project project = new Project();
        project.setId(3L);
        project.setName("Apollo");
        project.setDescription("d");
        project.setPriority(2);
        project.setBeginDate(LocalDate.of(2026, 5, 1));
        project.setDueDate(LocalDate.of(2026, 6, 1));
        project.setMaxManDays(10);
        project.setManager(manager);
        project.setMembers(members);
        project.setBlackboard(blackboard);

        ProjectDTO dto = ProjectDTO.from(project);
        assertEquals(3L, dto.id());
        assertEquals("Apollo", dto.name());
        assertEquals("d", dto.description());
        assertEquals(2, dto.priority());
        assertEquals(LocalDate.of(2026, 5, 1), dto.beginDate());
        assertEquals(LocalDate.of(2026, 6, 1), dto.dueDate());
        assertEquals(10, dto.maxManDays());
        assertEquals(5L, dto.managerId());
        assertEquals(1, dto.memberIds().size());
        assertTrue(dto.memberIds().contains(6L));
        assertEquals(8L, dto.blackboardId());
    }

    @Test
    @DisplayName("ProjectDTO.from is null-safe for a bare project (no manager/members/blackboard)")
    void projectDtoFromNullSafe() {
        Project bare = new Project();
        bare.setId(1L);

        ProjectDTO dto = ProjectDTO.from(bare);
        assertEquals(1L, dto.id());
        assertNull(dto.managerId());
        assertNull(dto.blackboardId());
        assertTrue(dto.memberIds().isEmpty(), "null member set maps to an empty list");
    }
}
