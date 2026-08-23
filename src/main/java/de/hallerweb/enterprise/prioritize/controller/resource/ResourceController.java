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

package de.hallerweb.enterprise.prioritize.controller.resource;

import de.hallerweb.enterprise.prioritize.config.AuthenticatedUser;
import de.hallerweb.enterprise.prioritize.dto.resource.ResourceDTO;
import de.hallerweb.enterprise.prioritize.dto.resource.ResourceGroupDTO;
import de.hallerweb.enterprise.prioritize.dto.resource.ResourceRequest;
import de.hallerweb.enterprise.prioritize.dto.resource.ResourceReservationDTO;
import de.hallerweb.enterprise.prioritize.dto.resource.ResourceStatusDTO;
import de.hallerweb.enterprise.prioritize.dto.resource.ResourceValueDTO;
import de.hallerweb.enterprise.prioritize.dto.skill.SkillRecordDTO;
import de.hallerweb.enterprise.prioritize.dto.skill.SkillRecordRequest;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceReservation;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.skill.SkillRecord;
import de.hallerweb.enterprise.prioritize.service.company.DepartmentService;
import de.hallerweb.enterprise.prioritize.service.resource.ResourceService;
import de.hallerweb.enterprise.prioritize.service.resource.control.ResourceControlService;
import de.hallerweb.enterprise.prioritize.service.skill.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;

@Tag(name = "Resources", description = "Manage resources and groups, reservations, control commands and telemetry ingest.")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;
    private final DepartmentService departmentService;
    private final SkillService skillService;
    private final ResourceControlService resourceControlService;

    /**
     * Helper method to determine the currently authenticated user.
     */

    /**
     * Returns all resources of a specific resource group.
     *
     * @param groupId ID of the resource group
     * @return ResponseEntity with the set of resources
     */
    @Operation(summary = "Returns all resources of a specific resource group")
    @GetMapping("/resourcegroups/{groupId}/resources")
    public ResponseEntity<List<ResourceDTO>> getResourcesByResourceGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(
                resourceService.getResourcesByGroupId(groupId).stream().map(ResourceDTO::from).toList());
    }

    /**
     * Returns the resource groups of a department. Without this, a REST consumer cannot discover any
     * group id: groups could be created and deleted, but never listed, so the group-scoped reads
     * ({@code /resourcegroups/{groupId}/resources}) had no reachable entry point.
     *
     * @param deptId ID of the department
     * @return ResponseEntity with the department's resource groups
     */
    @Operation(summary = "Returns the resource groups of a department")
    @GetMapping("/departments/{deptId}/resourcegroups")
    public ResponseEntity<List<ResourceGroupDTO>> getResourceGroups(
        @PathVariable Long deptId,
        @AuthenticatedUser PUser currentUser) {

        return ResponseEntity.ok(
                resourceService.getResourceGroupsByDepartment(deptId, currentUser).stream()
                        .map(ResourceGroupDTO::from)
                        .toList());
    }

    /**
     * Creates a new resource group for a specific department.
     *
     * @param deptId ID of the department
     * @param name   name of the new resource group
     * @return ResponseEntity with the newly created resource group
     */
    @Operation(summary = "Creates a new resource group for a specific department")
    @PostMapping("/departments/{deptId}/resourcegroups")
    public ResponseEntity<ResourceGroupDTO> createResourceGroup(
        @PathVariable Long deptId,
        @RequestParam String name,
        @AuthenticatedUser PUser currentUser) {

        Department dept = departmentService.getDepartmentById(deptId);
        ResourceGroup group = resourceService.createResourceGroup(name, dept, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(ResourceGroupDTO.from(group));
    }

    /**
     * Renames a resource group. The name is its only editable field, and the default group is
     * protected — the service rejects renaming it.
     *
     * @param groupId ID of the resource group
     * @param name    new name of the resource group
     * @return ResponseEntity with the renamed resource group
     */
    @Operation(summary = "Renames a resource group, if the current user is authorized")
    @PutMapping("/resourcegroups/{groupId}")
    public ResponseEntity<ResourceGroupDTO> renameResourceGroup(
        @PathVariable Long groupId,
        @RequestParam String name,
        @AuthenticatedUser PUser currentUser) {

        return ResponseEntity.ok(
                ResourceGroupDTO.from(resourceService.renameResourceGroup(groupId, name, currentUser)));
    }

    /**
     * Deletes a resource group, if the current user is authorized.
     *
     * @param groupId ID of the resource group to delete
     * @return ResponseEntity without body
     */
    @Operation(summary = "Deletes a resource group, if the current user is authorized")
    @DeleteMapping("/resourcegroups/{groupId}")
    public ResponseEntity<Void> deleteResourceGroup(
        @PathVariable Long groupId,
        @AuthenticatedUser PUser currentUser) {

        resourceService.deleteResourceGroup(groupId, currentUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * Creates a new resource in a specific resource group.
     *
     * @param groupId  ID of the resource group
     * @param resource resource to be created
     * @return ResponseEntity with the newly created resource
     */
    @Operation(summary = "Creates a new resource in a specific resource group")
    @PostMapping("/resourcegroups/{groupId}/resources")
    public ResponseEntity<ResourceDTO> createResource(
        @PathVariable Long groupId,
        @RequestBody ResourceRequest request,
        @AuthenticatedUser PUser currentUser) {

        Resource created = resourceService.createResource(request.toResource(), groupId, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(ResourceDTO.from(created));
    }

    /**
     * Returns every resource the current user may read, across all groups and departments. The flat
     * enumeration entry point for consumers (e.g. a dashboard): the per-group endpoints require group ids
     * that a REST client cannot otherwise discover.
     *
     * @return ResponseEntity with the list of readable resources
     */
    @Operation(summary = "Returns every resource the current user may read (flat list)")
    @GetMapping("/resources")
    public ResponseEntity<List<ResourceDTO>> getAllResources(@AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(
                resourceService.getAllResources(currentUser).stream().map(ResourceDTO::from).toList());
    }

    /**
     * Returns every readable resource together with its latest telemetry readings and its monitoring
     * rules — the same picture as {@code /resources} plus {@code /resources/{id}/values/latest} plus
     * {@code /resources/{id}/telemetry-rules}, but in a single call instead of 1&nbsp;+&nbsp;2N. Meant
     * for status views that poll: those paid the fan-out on every tick, and every one of those calls
     * re-authenticated and re-resolved the caller.
     *
     * @return ResponseEntity with the status of every readable resource
     */
    @Operation(summary = "Returns every readable resource with its latest values and monitoring rules")
    @GetMapping("/resources/status")
    public ResponseEntity<List<ResourceStatusDTO>> getResourceStatus(@AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(resourceService.getResourceStatus(currentUser));
    }

    /**
     * Retrieves a resource, if the current user is authorized.
     *
     * @param id ID of the resource
     * @return ResponseEntity with the resource
     */
    @Operation(summary = "Retrieves a resource, if the current user is authorized")
    @GetMapping("/resources/{id}")
    public ResponseEntity<ResourceDTO> getResource(
        @PathVariable Long id,
        @AuthenticatedUser PUser currentUser) {

        return ResponseEntity.ok(ResourceDTO.from(resourceService.getResource(id, currentUser)));
    }

    /**
     * Updates individual fields of a resource (PATCH semantics: null = unchanged).
     *
     * @param id    ID of the resource
     * @param patch resource with the fields to be changed
     * @return the updated resource
     */
    @Operation(summary = "Updates individual fields of a resource (PATCH semantics: null = unchanged)")
    @PatchMapping("/resources/{id}")
    public ResponseEntity<ResourceDTO> partialUpdateResource(
        @PathVariable Long id,
        @RequestBody ResourceRequest patch,
        @AuthenticatedUser PUser currentUser) {

        Resource updated = resourceService.partialUpdateResource(id, patch.toResource(), currentUser);
        return ResponseEntity.ok(ResourceDTO.from(updated));
    }

    /**
     * Removes the cost rate, so the resource is no longer charged for.
     * <p>
     * Its own endpoint because PATCH cannot express it: there {@code null} means "unchanged", which is
     * what makes a partial update partial, so no request body can say "remove this". The three cost
     * fields also only make sense together — clearing them one at a time would go through a state the
     * validation rejects. Both reasons are specific to removal, which is why this is a small endpoint
     * rather than a change to PATCH semantics across the API.
     *
     * @param id ID of the resource
     * @return the resource without its cost rate
     */
    @Operation(summary = "Removes a resource's cost rate")
    @DeleteMapping("/resources/{id}/cost-rate")
    public ResponseEntity<ResourceDTO> clearCostRate(
        @PathVariable Long id,
        @AuthenticatedUser PUser currentUser) {

        return ResponseEntity.ok(ResourceDTO.from(resourceService.clearCostRate(id, currentUser)));
    }

    /**
     * Deletes a resource, if the current user is authorized.
     *
     * @param id ID of the resource to delete
     * @return ResponseEntity without body
     */
    @Operation(summary = "Deletes a resource, if the current user is authorized")
    @DeleteMapping("/resources/{id}")
    public ResponseEntity<Void> deleteResource(
        @PathVariable Long id,
        @AuthenticatedUser PUser currentUser) {

        resourceService.deleteResource(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reserves a resource for a specific time span.
     *
     * @param id           ID of the resource
     * @param fromIsoDate  start date in ISO format (e.g. 2026-05-15T14:00:00Z)
     * @param untilIsoDate end date in ISO format (e.g. 2026-05-15T16:00:00Z)
     * @return ResponseEntity with the new reservation
     */
    @Operation(summary = "Reserves a resource for a specific time span")
    @PostMapping("/resources/{id}/reserve")
    public ResponseEntity<ResourceReservationDTO> reserveResource(
        @PathVariable Long id,
        @RequestParam String fromIsoDate,
        @RequestParam String untilIsoDate,
        @AuthenticatedUser PUser currentUser) {

        // IllegalArgumentException on invalid format is mapped to 400 by the GlobalExceptionHandler
        Instant from = Instant.parse(fromIsoDate);
        Instant until = Instant.parse(untilIsoDate);

        if (!until.isAfter(from)) {
            throw new IllegalArgumentException("Das Enddatum muss nach dem Startdatum liegen.");
        }

        ResourceReservation reservation = resourceService.reserveResource(id, currentUser, from, until);
        return ResponseEntity.status(HttpStatus.CREATED).body(ResourceReservationDTO.from(reservation));
    }

    /**
     * Returns the caller's own currently active reservations on
     * this resource. Useful as a preview of which slot a control command
     * derives: empty → command not possible; exactly one → its slot; multiple →
     * command ambiguous.
     *
     * @param id ID of the resource
     * @return list of the caller's own active reservations (possibly empty)
     */
    @Operation(summary = "Returns the caller's own currently active reservations on this resource")
    @GetMapping("/resources/{id}/reservations/mine")
    public ResponseEntity<List<ResourceReservationDTO>> getMyActiveReservations(
        @PathVariable Long id,
        @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(
            resourceService.getMyActiveReservations(id, currentUser)
                .stream().map(ResourceReservationDTO::from).toList());
    }

    /**
     * Returns all reservations of a resource (occupancy overview). Requires
     * read permission on the resource.
     *
     * @param id ID of the resource
     * @return list of all reservations of the resource
     */
    @Operation(summary = "Returns all reservations of a resource (occupancy overview)")
    @GetMapping("/resources/{id}/reservations")
    public ResponseEntity<List<ResourceReservationDTO>> getReservationsForResource(
        @PathVariable Long id,
        @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(
            resourceService.getReservationsForResourceDTO(id, currentUser));
    }

    /**
     * Cancels a reservation and releases the occupied slot. Allowed for the
     * owner of the reservation or a user with UPDATE rights on the resource.
     *
     * @param reservationId ID of the reservation to cancel
     * @return 204 No Content on success
     */
    @Operation(summary = "Cancels a reservation and releases the occupied slot")
    @DeleteMapping("/reservations/{reservationId}")
    public ResponseEntity<Void> cancelReservation(
        @PathVariable Integer reservationId,
        @AuthenticatedUser PUser currentUser) {
        resourceService.cancelReservation(reservationId, currentUser);
        return ResponseEntity.noContent().build();
    }


    // ==========================================
    // RESOURCE CONTROL (sending commands)
    // ==========================================

    /**
     * Sends a control command to a resource. The transport (MQTT/REST) is
     * chosen automatically (MQTT preferred when online, otherwise REST fallback). The
     * addressed slot is derived server-side from the user's active reservation
     * — the client supplies no slot.
     *
     * @param id      ID of the resource
     * @param request command and optional free parameter
     * @return 202 Accepted, if the command was handed off
     */
    @Operation(summary = "Sends a control command to a resource")
    @PostMapping("/resources/{id}/command")
    public ResponseEntity<Void> sendCommand(
        @PathVariable Long id,
        @RequestBody ResourceCommandRequest request,
        @AuthenticatedUser PUser currentUser) {

        Resource resource = resourceService.getResource(id, currentUser);
        resourceControlService.sendCommand(resource, request.command(), request.param(), currentUser);
        return ResponseEntity.accepted().build();
    }

    /**
     * Request body for a control command. {@code param} is optional.
     */
    public record ResourceCommandRequest(String command, String param) {
    }

    // ==========================================
    // TELEMETRY (REST ingest)
    // ==========================================

    /**
     * Records a telemetry reading for a resource (REST ingest). Enables devices without MQTT —
     * and manual/Bruno calls — to report values. The reading is appended to the named data
     * point's history, mirroring the MQTT VALUE path. Requires UPDATE permission on the
     * resource.
     *
     * @param id      ID of the resource
     * @param request data point name and value (both mandatory)
     * @return 202 Accepted once the reading has been recorded
     */
    @Operation(summary = "Records a telemetry reading for a resource (REST ingest)")
    @PostMapping("/resources/{id}/values")
    public ResponseEntity<Void> recordValue(
        @PathVariable Long id,
        @RequestBody ResourceValueRequest request,
        @AuthenticatedUser PUser currentUser) {

        if (request == null || request.name() == null || request.name().isBlank()
            || request.value() == null) {
            throw new IllegalArgumentException("name and value are required.");
        }
        resourceService.recordMqttValue(id, request.name(), request.value(), currentUser);
        return ResponseEntity.accepted().build();
    }

    /**
     * Request body for a telemetry ingest. Both fields are mandatory.
     */
    public record ResourceValueRequest(String name, String value) {
    }

    /**
     * Returns the newest reading of every telemetry data point of a resource — the read counterpart to the
     * ingest above. A dashboard renders one value per data point without pulling the full history. Requires
     * READ permission on the resource.
     *
     * @param id ID of the resource
     * @return ResponseEntity with the latest value per data point (empty when none recorded)
     */
    @Operation(summary = "Returns the newest reading of every telemetry data point of a resource")
    @GetMapping("/resources/{id}/values/latest")
    public ResponseEntity<List<ResourceValueDTO>> getLatestValues(
        @PathVariable Long id,
        @AuthenticatedUser PUser currentUser) {

        return ResponseEntity.ok(resourceService.getLatestValues(id, currentUser));
    }

    // ==========================================
    // SKILL RECORDS - RESOURCE ASSIGNMENT
    // ==========================================

    /**
     * Returns all skills of a resource.
     *
     * @param resourceId ID of the resource
     * @return ResponseEntity with the set of SkillRecords
     */
    @Operation(summary = "Returns all skills of a resource")
    @GetMapping("/resources/{resourceId}/skills")
    public ResponseEntity<List<SkillRecordDTO>> getSkillsForResource(@PathVariable Long resourceId) {
        return ResponseEntity.ok(
                skillService.getSkillsForResource(resourceId).stream().map(SkillRecordDTO::from).toList());
    }

    /**
     * Returns all skills of a resource, filtered by resource group.
     * Additionally validates whether the resource belongs to the given group.
     *
     * @param groupId    ID of the resource group
     * @param resourceId ID of the resource
     * @return ResponseEntity with the set of SkillRecords
     */
    @Operation(summary = "Returns all skills of a resource, filtered by resource group")
    @GetMapping("/resourcegroups/{groupId}/resources/{resourceId}/skills")
    public ResponseEntity<List<SkillRecordDTO>> getSkillsForResourceInGroup(
        @PathVariable Long groupId,
        @PathVariable Long resourceId) {

        resourceService.validateResourceInGroup(resourceId, groupId);
        return ResponseEntity.ok(
                skillService.getSkillsForResource(resourceId).stream().map(SkillRecordDTO::from).toList());
    }

    /**
     * Assigns a skill to a resource.
     *
     * @param resourceId ID of the resource
     * @param record     The SkillRecord to assign
     * @return ResponseEntity with the created SkillRecord
     */
    @Operation(summary = "Assigns a skill to a resource")
    @PostMapping("/resources/{resourceId}/skills")
    public ResponseEntity<SkillRecordDTO> assignSkillToResource(
        @PathVariable Long resourceId,
        @RequestBody SkillRecordRequest request) {

        SkillRecord assignedRecord = skillService.assignSkillToResource(resourceId, request.toSkillRecord());
        return ResponseEntity.status(HttpStatus.CREATED).body(SkillRecordDTO.from(assignedRecord));
    }
}