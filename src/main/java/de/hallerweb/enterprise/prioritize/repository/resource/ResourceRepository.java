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

import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    // Standard search by name
    List<Resource> findByNameContainingIgnoreCase(String name);

    // Finds all resources of a specific department
    List<Resource> findByDepartment_Id(Long departmentId);

    // Finds resources that are currently not occupied (busy = false)
    List<Resource> findByBusyFalse();

    // Special search for MQTT devices that are online
    List<Resource> findByMqttResourceTrueAndMqttOnlineTrue();

    // The "large" search with filters (similar to the one for Company)
    @Query("SELECT r FROM Resource r WHERE " +
            "(:name IS NULL OR LOWER(r.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:description IS NULL OR LOWER(r.description) LIKE LOWER(CONCAT('%', :description, '%'))) AND " +
            "(:departmentId IS NULL OR r.department.id = :departmentId) AND " +
            "(:mqttResource IS NULL OR r.mqttResource = :mqttResource)")
    List<Resource> findResourcesByFilter(
            @Param("name") String name,
            @Param("description") String description,
            @Param("departmentId") Integer departmentId,
            @Param("mqttResource") Boolean mqttResource
    );

    // Finds all resources of a specific group
    List<Resource> findByResourceGroup_Id(Long groupId);

    // Paged variant for the admin resources grid (lazy CallbackDataProvider)
    Page<Resource> findByResourceGroup_Id(Long groupId, Pageable pageable);

    long countByResourceGroup_Id(Long groupId);

    // Helpful for MQTT updates
    Optional<Resource> findByMqttUUID(String mqttUUID);
}