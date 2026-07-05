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

package de.hallerweb.enterprise.prioritize.repository.document;

import de.hallerweb.enterprise.prioritize.model.document.DocumentGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for DocumentGroup entities (directories).
 */
@Repository
public interface DocumentGroupRepository extends JpaRepository<DocumentGroup, Long> {

    // Finds all document groups of a specific department
    List<DocumentGroup> findByDepartment_Id(Long departmentId);

    // Finds a specific group by name within a department
    // Essential for the check on the "default" group in the DataInitializer
    Optional<DocumentGroup> findByNameAndDepartment_Id(String name, Long departmentId);

    // Finds groups by name (global, case-insensitive)
    List<DocumentGroup> findByNameContainingIgnoreCase(String name);
}