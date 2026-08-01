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

package de.hallerweb.enterprise.prioritize.dto.company;

import de.hallerweb.enterprise.prioritize.dto.address.AddressDTO;
import de.hallerweb.enterprise.prioritize.model.company.Department;

/**
 * Flat, transport-safe view of a {@link Department}. Carries the scalar fields, the eager {@code address}
 * and the owning company's id, but never the lazy {@code documentGroups}/{@code resourceGroups}
 * collections nor the {@code company} back-reference itself — so serializing a department never triggers
 * a {@code LazyInitializationException}. {@code company.getId()} reads the foreign key off the proxy
 * without initializing it, so this is safe outside a transaction.
 *
 * @author peter haller
 */
public record DepartmentDTO(Long id,
                            String name,
                            String description,
                            Long companyId,
                            AddressDTO address) {

    /** Maps an entity to its DTO. */
    public static DepartmentDTO from(Department department) {
        return new DepartmentDTO(
                department.getId(),
                department.getName(),
                department.getDescription(),
                department.getCompany() != null ? department.getCompany().getId() : null,
                AddressDTO.from(department.getAddress()));
    }
}
