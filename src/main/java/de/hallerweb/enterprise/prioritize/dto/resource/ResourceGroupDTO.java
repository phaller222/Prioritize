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

package de.hallerweb.enterprise.prioritize.dto.resource;

import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;

/**
 * Flat, transport-safe view of a {@link ResourceGroup}. Carries the scalar name plus the owning department
 * by id, never the lazy {@code resources} collection. The department id is read off the lazy proxy without
 * initializing it.
 *
 * @author peter haller
 */
public record ResourceGroupDTO(Long id,
                               String name,
                               Long departmentId) {

    /** Maps an entity to its DTO. */
    public static ResourceGroupDTO from(ResourceGroup group) {
        return new ResourceGroupDTO(
                group.getId(),
                group.getName(),
                group.getDepartment() != null ? group.getDepartment().getId() : null);
    }
}
