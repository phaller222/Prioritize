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

package de.hallerweb.enterprise.prioritize.dto.document;

import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.document.DocumentGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Plain unit tests (no Spring context) for the request/response DTO mapping of the document-group endpoints.
 */
class DocumentGroupDtoMappingTest {

    @Test
    @DisplayName("DocumentGroupRequest.toDocumentGroup sets name + a department stub, leaves id null")
    void requestToEntity() {
        DocumentGroup g = new DocumentGroupRequest("Specs", 4L).toDocumentGroup();
        assertNull(g.getId());
        assertEquals("Specs", g.getName());
        assertEquals(4L, g.getDepartment().getId(), "department is referenced by id only");

        assertNull(new DocumentGroupRequest("Loose", null).toDocumentGroup().getDepartment());
    }

    @Test
    @DisplayName("DocumentGroupDTO.from carries name + department id, null-safe")
    void dtoFrom() {
        Department dept = new Department();
        dept.setId(4L);
        DocumentGroup g = DocumentGroup.builder().name("Specs").build();
        g.setId(9L);
        g.setDepartment(dept);

        DocumentGroupDTO dto = DocumentGroupDTO.from(g);
        assertEquals(9L, dto.id());
        assertEquals("Specs", dto.name());
        assertEquals(4L, dto.departmentId());

        DocumentGroup orphan = DocumentGroup.builder().name("Loose").build();
        orphan.setId(10L);
        assertNull(DocumentGroupDTO.from(orphan).departmentId());
    }
}
