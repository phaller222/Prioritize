package de.hallerweb.enterprise.prioritize.controller.resource;

import de.hallerweb.enterprise.prioritize.config.CurrentUserResolver;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;
    private final CurrentUserResolver currentUserResolver;
    private final DepartmentService departmentService;
    private final SkillService skillService;
    private final ResourceControlService resourceControlService;

    /**
     * Helper method to determine the currently authenticated user.
     */
    private PUser getCurrentUser(Authentication auth) {
        return currentUserResolver.resolve(auth);
    }


    /**
     * Returns all resources of a specific resource group.
     *
     * @param groupId ID of the resource group
     * @return ResponseEntity with the set of resources
     */
    @GetMapping("/resourcegroups/{groupId}/resources")
    public ResponseEntity<Set<Resource>> getResourcesByResourceGroup(@PathVariable Long groupId) {
        Set<Resource> resources = resourceService.getResourcesByGroupId(groupId);
        return ResponseEntity.ok(resources);
    }


    /**
     * Creates a new resource group for a specific department.
     *
     * @param deptId ID of the department
     * @param name   name of the new resource group
     * @return ResponseEntity with the newly created resource group
     */
    @PostMapping("/departments/{deptId}/resourcegroups")
    public ResponseEntity<ResourceGroup> createResourceGroup(
        @PathVariable Long deptId,
        @RequestParam String name,
        Authentication auth) {

        Department dept = departmentService.getDepartmentById(deptId);
        ResourceGroup group = resourceService.createResourceGroup(name, dept, getCurrentUser(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(group);
    }


    /**
     * Deletes a resource group, if the current user is authorized.
     *
     * @param groupId ID of the resource group to delete
     * @return ResponseEntity without body
     */
    @DeleteMapping("/resourcegroups/{groupId}")
    public ResponseEntity<Void> deleteResourceGroup(
        @PathVariable Long groupId,
        Authentication auth) {

        resourceService.deleteResourceGroup(groupId, getCurrentUser(auth));
        return ResponseEntity.noContent().build();
    }


    /**
     * Creates a new resource in a specific resource group.
     *
     * @param groupId  ID of the resource group
     * @param resource resource to be created
     * @return ResponseEntity with the newly created resource
     */
    @PostMapping("/resourcegroups/{groupId}/resources")
    public ResponseEntity<Resource> createResource(
        @PathVariable Long groupId,
        @RequestBody Resource resource,
        Authentication auth) {

        Resource created = resourceService.createResource(resource, groupId, getCurrentUser(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }


    /**
     * Retrieves a resource, if the current user is authorized.
     *
     * @param id ID of the resource
     * @return ResponseEntity with the resource
     */
    @GetMapping("/resources/{id}")
    public ResponseEntity<Resource> getResource(
        @PathVariable Long id,
        Authentication auth) {

        return ResponseEntity.ok(resourceService.getResource(id, getCurrentUser(auth)));
    }

    /**
     * Updates individual fields of a resource (PATCH semantics: null = unchanged).
     *
     * @param id    ID of the resource
     * @param patch resource with the fields to be changed
     * @return the updated resource
     */
    @PatchMapping("/resources/{id}")
    public ResponseEntity<Resource> partialUpdateResource(
        @PathVariable Long id,
        @RequestBody Resource patch,
        Authentication auth) {

        return ResponseEntity.ok(resourceService.partialUpdateResource(id, patch, getCurrentUser(auth)));
    }

    /**
     * Deletes a resource, if the current user is authorized.
     *
     * @param id ID of the resource to delete
     * @return ResponseEntity without body
     */
    @DeleteMapping("/resources/{id}")
    public ResponseEntity<Void> deleteResource(
        @PathVariable Long id,
        Authentication auth) {

        resourceService.deleteResource(id, getCurrentUser(auth));
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
    @PostMapping("/resources/{id}/reserve")
    public ResponseEntity<ResourceReservation> reserveResource(
        @PathVariable Long id,
        @RequestParam String fromIsoDate,
        @RequestParam String untilIsoDate,
        Authentication auth) {

        // IllegalArgumentException on invalid format is mapped to 400 by the GlobalExceptionHandler
        Instant from = Instant.parse(fromIsoDate);
        Instant until = Instant.parse(untilIsoDate);

        if (!until.isAfter(from)) {
            throw new IllegalArgumentException("Das Enddatum muss nach dem Startdatum liegen.");
        }

        ResourceReservation reservation = resourceService.reserveResource(id, getCurrentUser(auth), from, until);
        return ResponseEntity.status(HttpStatus.CREATED).body(reservation);
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
    @GetMapping("/resources/{id}/reservations/mine")
    public ResponseEntity<List<ResourceReservation>> getMyActiveReservations(
        @PathVariable Long id,
        Authentication auth) {
        return ResponseEntity.ok(
            resourceService.getMyActiveReservations(id, getCurrentUser(auth)));
    }

    /**
     * Returns all reservations of a resource (occupancy overview). Requires
     * read permission on the resource.
     *
     * @param id ID of the resource
     * @return list of all reservations of the resource
     */
    @GetMapping("/resources/{id}/reservations")
    public ResponseEntity<List<ResourceReservation>> getReservationsForResource(
        @PathVariable Long id,
        Authentication auth) {
        return ResponseEntity.ok(
            resourceService.getReservationsForResource(id, getCurrentUser(auth)));
    }

    /**
     * Cancels a reservation and releases the occupied slot. Allowed for the
     * owner of the reservation or a user with UPDATE rights on the resource.
     *
     * @param reservationId ID of the reservation to cancel
     * @return 204 No Content on success
     */
    @DeleteMapping("/reservations/{reservationId}")
    public ResponseEntity<Void> cancelReservation(
        @PathVariable Integer reservationId,
        Authentication auth) {
        resourceService.cancelReservation(reservationId, getCurrentUser(auth));
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
    @PostMapping("/resources/{id}/command")
    public ResponseEntity<Void> sendCommand(
        @PathVariable Long id,
        @RequestBody ResourceCommandRequest request,
        Authentication auth) {

        Resource resource = resourceService.getResource(id, getCurrentUser(auth));
        resourceControlService.sendCommand(resource, request.command(), request.param(), getCurrentUser(auth));
        return ResponseEntity.accepted().build();
    }

    /**
     * Request body for a control command. {@code param} is optional.
     */
    public record ResourceCommandRequest(String command, String param) {
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
    @GetMapping("/resources/{resourceId}/skills")
    public ResponseEntity<Set<SkillRecord>> getSkillsForResource(@PathVariable Long resourceId) {
        return ResponseEntity.ok(skillService.getSkillsForResource(resourceId));
    }

    /**
     * Returns all skills of a resource, filtered by resource group.
     * Additionally validates whether the resource belongs to the given group.
     *
     * @param groupId    ID of the resource group
     * @param resourceId ID of the resource
     * @return ResponseEntity with the set of SkillRecords
     */
    @GetMapping("/resourcegroups/{groupId}/resources/{resourceId}/skills")
    public ResponseEntity<Set<SkillRecord>> getSkillsForResourceInGroup(
        @PathVariable Long groupId,
        @PathVariable Long resourceId) {

        resourceService.validateResourceInGroup(resourceId, groupId);
        return ResponseEntity.ok(skillService.getSkillsForResource(resourceId));
    }

    /**
     * Assigns a skill to a resource.
     *
     * @param resourceId ID of the resource
     * @param record     The SkillRecord to assign
     * @return ResponseEntity with the created SkillRecord
     */
    @PostMapping("/resources/{resourceId}/skills")
    public ResponseEntity<SkillRecord> assignSkillToResource(
        @PathVariable Long resourceId,
        @RequestBody SkillRecord record) {

        SkillRecord assignedRecord = skillService.assignSkillToResource(resourceId, record);
        return ResponseEntity.status(HttpStatus.CREATED).body(assignedRecord);
    }
}