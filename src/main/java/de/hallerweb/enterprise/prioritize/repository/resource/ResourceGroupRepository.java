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

package de.hallerweb.enterprise.prioritize.repository.resource;

import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ResourceGroup entities.
 */
@Repository
public interface ResourceGroupRepository extends JpaRepository<ResourceGroup, Long> {

    // Finds all groups of a specific department
    List<ResourceGroup> findByDepartment_Id(Long departmentId);

    // Finds a specific group by name within a department
    // Important for the check on the "default" group
    Optional<ResourceGroup> findByNameAndDepartment_Id(String name, int departmentId);

    // Finds groups by name (global)
    List<ResourceGroup> findByNameContainingIgnoreCase(String name);
}