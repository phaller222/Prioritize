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

/**
 * Request body for creating a {@link DocumentGroup}. Carries the group name and the owning department by
 * id (the service resolves nothing further — it stores the group with that department FK). Never an
 * {@code id} nor the {@code documents} collection.
 *
 * @author peter haller
 */
public record DocumentGroupRequest(String name,
                                   Long departmentId) {

    /** Builds a fresh, id-less {@link DocumentGroup} carrying a department stub (id only), if one was given. */
    public DocumentGroup toDocumentGroup() {
        DocumentGroup group = DocumentGroup.builder()
                .name(name)
                .build();
        if (departmentId != null) {
            Department departmentRef = new Department();
            departmentRef.setId(departmentId);
            group.setDepartment(departmentRef);
        }
        return group;
    }
}
