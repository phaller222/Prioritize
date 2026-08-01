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

import de.hallerweb.enterprise.prioritize.dto.address.AddressRequest;
import de.hallerweb.enterprise.prioritize.model.company.Department;

/**
 * Request body for creating or updating a {@link Department}. Carries only the writable base data — the
 * owning company comes from the path, never the body, and the lazy group collections and secret
 * {@code token} are not exposed. On update the service copies these scalar fields onto the managed entity.
 *
 * @author peter haller
 */
public record DepartmentRequest(String name,
                                String description,
                                AddressRequest address) {

    /** Builds a fresh, id-less {@link Department} (with its address) from this request. */
    public Department toDepartment() {
        return Department.builder()
                .name(name)
                .description(description)
                .address(address != null ? address.toAddress() : null)
                .build();
    }
}
