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

package de.hallerweb.enterprise.prioritize.repository.company;

import de.hallerweb.enterprise.prioritize.model.company.Department;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    // Overrides the normal findAll and eagerly loads the Company and the resource groups
    @EntityGraph(attributePaths = {"company", "resourceGroups"})
    List<Department> findAll();

    // Finds a department by its name
    Optional<Department> findByName(String name);

    // Finds a department by its secret token
    // Extremely important for automated processes/devices
    Optional<Department> findByToken(String token);

    // Finds all departments of a specific company
    List<Department> findByCompany_Id(Long companyId);

    // Enables a search by department name (case insensitive)
    List<Department> findByNameContainingIgnoreCase(String name);
}