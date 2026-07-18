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

package de.hallerweb.enterprise.prioritize.service.resource;

import de.hallerweb.enterprise.prioritize.dto.resource.ResourceReservationDTO;
import de.hallerweb.enterprise.prioritize.dto.resource.ResourceSummaryDTO;
import de.hallerweb.enterprise.prioritize.model.calendar.TimeSpan;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.resource.NameValueEntry;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceReservation;
import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceGroupRepository;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceRepository;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceReservationRepository;
import de.hallerweb.enterprise.prioritize.service.security.AuthorizationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Log4j2
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final ResourceGroupRepository resourceGroupRepository;
    private final ResourceReservationRepository reservationRepository;
    private final AuthorizationService authService; // Your central guard

    /** Maximum number of readings kept per data point in the comma-separated history. */
    private static final int MAX_VALUE_HISTORY = 100;

    /** Canonical type name of {@link Department} for the string-based authorization overload. */
    private static final String DEPARTMENT_TYPE = "de.hallerweb.enterprise.prioritize.model.company.Department";

    // --- Resource Group Management ---

    public ResourceGroup createResourceGroup(String name, Department dept, PUser user) {
        // Here we check whether the user is even allowed to create groups in the department
        // In case the department itself is a PAuthorizedObject:
        if (!authService.hasPermission(user, dept, Action.CREATE)) {
            throw new AccessDeniedException("No permission to create groups in this department.");
        }

        ResourceGroup group = ResourceGroup.builder()
                .name(name)
                .department(dept)
                .build();
        return resourceGroupRepository.save(group);
    }

    /**
     * Lists the resource groups of a department (for the admin group management screen). Requires
     * {@link Action#READ} on the department, mirroring
     * {@link de.hallerweb.enterprise.prioritize.service.company.DepartmentService#getDepartmentsByCompany}.
     */
    @Transactional(readOnly = true)
    public List<ResourceGroup> getResourceGroupsByDepartment(Long departmentId, PUser user) {
        if (!authService.hasPermission(user, DEPARTMENT_TYPE, departmentId, Action.READ)) {
            throw new AccessDeniedException("No permission to read resource groups of this department.");
        }
        return resourceGroupRepository.findByDepartment_Id(departmentId);
    }

    /**
     * Renames a resource group (the only editable field). The default group is protected — as with
     * {@link #deleteResourceGroup} it cannot be renamed — and the caller needs {@link Action#UPDATE} on it.
     */
    public ResourceGroup renameResourceGroup(Long groupId, String newName, PUser user) {
        ResourceGroup group = resourceGroupRepository.findById(groupId)
                .orElseThrow(() -> new NoSuchElementException("Resource group not found."));
        if (ResourceGroup.DEFAULT_GROUP_NAME.equalsIgnoreCase(group.getName())) {
            throw new IllegalStateException("The default group cannot be renamed.");
        }
        if (!authService.hasPermission(user, group, Action.UPDATE)) {
            throw new AccessDeniedException("No permission to update this group.");
        }
        group.setName(newName);
        return resourceGroupRepository.save(group);
    }


    // --- Resource Management ---

    public Resource createResource(Resource resource, Long groupId, PUser user) {
        ResourceGroup group = resourceGroupRepository.findById(groupId)
                .orElseThrow(() -> new NoSuchElementException("Target group not found"));

        // Is the user allowed to create resources in this group?
        if (!authService.hasPermission(user, group, Action.UPDATE)) {
            throw new AccessDeniedException("No permission to add resources to this group.");
        }

        resource.setResourceGroup(group);
        resource.setDepartment(group.getDepartment());

        if (resource.getAgent() == null) {
            resource.setAgent(false);
        }
        if (resource.getStationary() == null) {
            resource.setStationary(false);
        }
        if (resource.getRemote() == null) {
            resource.setRemote(false);
        }
        if (resource.getBusy() == null) {
            resource.setBusy(false);
        }
        if (resource.getMqttResource() == null) {
            resource.setMqttResource(false);
        }
        if (resource.getMqttOnline() == null) {
            resource.setMqttOnline(false);
        }
        if (resource.getCurrentOccupiedSlots() == null) {
            resource.setCurrentOccupiedSlots(0);
        }
        if (resource.getMaxSlots() == null) {
            resource.setMaxSlots(1);
        }
        if (resource.getPort() == null) {
            resource.setPort(80);
        }

        return resourceRepository.save(resource);
    }

    public Resource getResource(Long resourceId, PUser user) {
        Resource res = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new NoSuchElementException("Resource not found"));

        if (!authService.hasPermission(user, res, Action.READ)) {
            throw new AccessDeniedException("No read permission for this resource.");
        }

        Instant now = Instant.now();
        long occupied = reservationRepository.findOverlappingReservations(resourceId, now, now.plusMillis(1))
                .size();
        res.setCurrentOccupiedSlots((int) occupied);
        return res;
    }

    @Transactional(readOnly = true)
    public Set<Resource> getResourcesByGroupId(Long groupId) {
        if (!resourceGroupRepository.existsById(groupId)) {
            throw new EntityNotFoundException("Resource group with id " + groupId + " not found");
        }
        return new java.util.HashSet<>(resourceRepository.findByResourceGroup_Id(groupId));
    }

    /**
     * Lists a resource group's resources for the admin resources grid, as a page of
     * {@link ResourceSummaryDTO}. Requires {@link Action#READ} on the group, mirroring the paged
     * {@link de.hallerweb.enterprise.prioritize.service.document.DocumentService#getDocumentsInGroup}
     * precedent. Mapped to a DTO (not the entity) so the rows are safe inside a Vaadin grid's key mapper —
     * {@code Resource}'s all-fields {@code hashCode} would otherwise touch its lazy relations. For each row
     * the currently occupied slots are derived from the reservations overlapping "now", exactly as
     * {@link #getResource} does for a single resource.
     */
    @Transactional(readOnly = true)
    public Page<ResourceSummaryDTO> getResourcesInGroup(Long groupId, PUser user, Pageable pageable) {
        ResourceGroup group = resourceGroupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Resource group with id " + groupId + " not found"));
        if (!authService.hasPermission(user, group, Action.READ)) {
            throw new AccessDeniedException("No permission to read resources of this group.");
        }
        Instant now = Instant.now();
        return resourceRepository.findByResourceGroup_Id(groupId, pageable)
                .map(r -> toSummary(r, now));
    }

    /**
     * Counts the resources in a group for the lazy data provider. Same {@link Action#READ} check on the
     * group as {@link #getResourcesInGroup}.
     */
    @Transactional(readOnly = true)
    public long countResourcesInGroup(Long groupId, PUser user) {
        ResourceGroup group = resourceGroupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Resource group with id " + groupId + " not found"));
        if (!authService.hasPermission(user, group, Action.READ)) {
            throw new AccessDeniedException("No permission to read resources of this group.");
        }
        return resourceRepository.countByResourceGroup_Id(groupId);
    }

    private ResourceSummaryDTO toSummary(Resource r, Instant now) {
        int occupied = reservationRepository.findOverlappingReservations(r.getId(), now, now.plusMillis(1)).size();
        return new ResourceSummaryDTO(
                r.getId(),
                r.getName(),
                r.getDescription(),
                Boolean.TRUE.equals(r.getMqttResource()),
                Boolean.TRUE.equals(r.getMqttOnline()),
                r.getMqttLastPing(),
                r.getMaxSlots() != null ? r.getMaxSlots() : 0,
                occupied);
    }

    /**
     * Returns a resource's reservations (past, active and future) flattened to {@link ResourceReservationDTO}
     * for the admin occupancy overview. Mapping happens inside the read-only transaction so the lazy
     * {@code reservedBy}/{@code timespan} are resolved here and the view never touches detached relations.
     * Same {@link Action#READ} permission as {@link #getReservationsForResource}.
     */
    @Transactional(readOnly = true)
    public List<ResourceReservationDTO> getReservationsForResourceDTO(Long resourceId, PUser user) {
        return getReservationsForResource(resourceId, user).stream()
                .map(res -> new ResourceReservationDTO(
                        res.getId(),
                        res.getReservedBy() != null ? res.getReservedBy().getUsername() : null,
                        res.getTimespan() != null ? res.getTimespan().getDateFrom() : null,
                        res.getTimespan() != null ? res.getTimespan().getDateUntil() : null,
                        res.getSlotNumber()))
                .toList();
    }


    // --- Reservierungs-Logik ---

    public ResourceReservation reserveResource(Long resourceId, PUser user, Instant from, Instant until) {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new NoSuchElementException("Resource not found"));

        // 1. Check rights
        if (!authService.hasPermission(user, resource, Action.READ)) {
            throw new AccessDeniedException("No permission for this resource.");
        }

        // 2. Find overlaps for the time span
        List<ResourceReservation> overlaps = reservationRepository
                .findOverlappingReservations(resourceId, from, until);

        // 3. Freien Slot berechnen
        int assignedSlot = findFirstAvailableSlot(resource.getMaxSlots(), overlaps);
        if (assignedSlot == -1) {
            throw new IllegalStateException("All slots (" + resource.getMaxSlots() + ") occupied.");
        }

        // 4. Reservierung inkl. TimeSpan speichern
        TimeSpan ts = TimeSpan.builder()
                .dateFrom(from)
                .dateUntil(until)
                .type(TimeSpan.TimeSpanType.RESOURCE_RESERVATION)
                .build();

        ResourceReservation reservation = ResourceReservation.builder()
                .resource(resource)
                .reservedBy(user)
                .timespan(ts)
                .slotNumber(assignedSlot)
                .build();

        return reservationRepository.save(reservation);
    }

    // --- Reservierungs-Abfrage & Storno ---

    /**
     * Returns the reservations active at the point in time {@code now} that the given
     * user holds on the resource. This is exactly the set from which the slot is
     * derived when sending a control command: empty → no command possible (409);
     * exactly one → its slot; multiple → ambiguous (409).
     *
     * @param resourceId resource
     * @param user       the querying user (also the owner of the reservations)
     * @return active reservations of this user on this resource
     */
    @Transactional(readOnly = true)
    public List<ResourceReservation> getMyActiveReservations(Long resourceId, PUser user) {
        Resource resource = resourceRepository.findById(resourceId)
            .orElseThrow(() -> new NoSuchElementException("Resource not found"));

        if (!authService.hasPermission(user, resource, Action.READ)) {
            throw new AccessDeniedException("No read permission for this resource.");
        }

        return reservationRepository.findActiveReservationsByUser(resourceId, user.getId(), Instant.now());
    }

    /**
     * Returns all reservations of a resource (past, active and future) as an
     * occupancy overview. Requires read permission on the resource.
     *
     * @param resourceId resource
     * @param user       the querying user (permission check)
     * @return all reservations of the resource
     */
    @Transactional(readOnly = true)
    public List<ResourceReservation> getReservationsForResource(Long resourceId, PUser user) {
        Resource resource = resourceRepository.findById(resourceId)
            .orElseThrow(() -> new NoSuchElementException("Resource not found"));

        if (!authService.hasPermission(user, resource, Action.READ)) {
            throw new AccessDeniedException("No read permission for this resource.");
        }

        return reservationRepository.findByResourceId(resourceId.intValue());
    }

    /**
     * Cancels (deletes) a reservation and thereby releases the occupied slot.
     * <p>
     * Permission: the caller must either be the owner of the reservation OR
     * hold {@link Action#UPDATE} on the associated resource (managers may
     * release others' reservations). Consistent with the convention: check in the service,
     * enforcement via exception.
     *
     * @param reservationId reservation to cancel
     * @param user          the executing user
     * @throws NoSuchElementException if the reservation does not exist
     * @throws AccessDeniedException  if the user is neither the owner nor has UPDATE rights
     */
    public void cancelReservation(Integer reservationId, PUser user) {
        ResourceReservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new NoSuchElementException(
                "Reservation " + reservationId + " not found."));

        boolean isOwner = reservation.getReservedBy() != null
            && user.getId().equals(reservation.getReservedBy().getId());
        boolean isResourceManager = authService.hasPermission(
            user, reservation.getResource(), Action.UPDATE);

        if (!isOwner && !isResourceManager) {
            throw new AccessDeniedException(
                "No permission to cancel this reservation.");
        }

        reservationRepository.delete(reservation);
        log.info("Reservation {} (slot {}) on resource {} cancelled by user '{}'.",
            reservationId, reservation.getSlotNumber(),
            reservation.getResource() != null ? reservation.getResource().getId() : "?",
            user.getUsername());
    }


    /**
     * Finds the first free slot number between 1 and maxSlots.
     *
     * @return free slot, or -1 if everything is occupied
     */
    private int findFirstAvailableSlot(int maxSlots, List<ResourceReservation> overlaps) {
        Set<Integer> occupied = overlaps.stream()
                .map(ResourceReservation::getSlotNumber)
                .collect(Collectors.toSet());

        for (int i = 1; i <= maxSlots; i++) {
            if (!occupied.contains(i)) return i;
        }
        return -1;
    }


    @Transactional(readOnly = true)
    public boolean isResourceAvailable(Long resourceId, Instant from, Instant until) {
        Resource resource = resourceRepository.findById(resourceId).orElseThrow();
        List<ResourceReservation> overlaps = reservationRepository.findOverlappingReservations(resourceId, from, until);
        return overlaps.size() < resource.getMaxSlots();
    }

    /**
     * Deletes a resource group.
     * * @param groupId ID of the group to delete
     *
     * @param user The executing user (for the permission check)
     */
    public void deleteResourceGroup(Long groupId, PUser user) {
        ResourceGroup group = resourceGroupRepository.findById(groupId)
                .orElseThrow(() -> new NoSuchElementException("Resource group not found."));

        // 1. Protection of the default group (system invariant)
        if (ResourceGroup.DEFAULT_GROUP_NAME.equalsIgnoreCase(group.getName())) {
            throw new IllegalStateException("The default group cannot be deleted.");
        }

        // 2. Permission check
        if (!authService.hasPermission(user, group, Action.DELETE)) {
            throw new AccessDeniedException("No permission to delete this group.");
        }

        // 3. Deletion
        // Note: if there are resources in the group, the cascade label in the model decides
        // whether they are deleted along with it or deletion is prevented.
        resourceGroupRepository.delete(group);
        log.info("Resource group '{}' (id: {}) deleted by user '{}'.",
                group.getName(), groupId, user.getUsername());
    }

    /**
     * Deletes a single resource and all reservations associated with it.
     * * @param resourceId ID of the resource
     *
     * @param user The executing user
     */
    public void deleteResource(Long resourceId, PUser user) {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new NoSuchElementException("Resource not found."));

        // 1. Permission check
        if (!authService.hasPermission(user, resource, Action.DELETE)) {
            throw new AccessDeniedException("No permission to delete this resource.");
        }

        // 2. Optional: Check auf aktive Reservierungen
        // Here you could decide whether deletion is forbidden while someone is still using the resource.
        List<ResourceReservation> activeReservations = reservationRepository.findOverlappingReservations(
                resourceId, Instant.now(), Instant.now().plus(Duration.ofDays(365)));

        if (!activeReservations.isEmpty()) {
            log.warn("Deleting resource {} despite {} future reservations.", resourceId, activeReservations.size());
            // Either throw an exception or (as planned here) delete it along via cascade.
        }

        // 3. Deletion
        resourceRepository.delete(resource);
    }

    public Resource partialUpdateResource(Long id, Resource patch, PUser user) {
        Resource existing = resourceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Resource not found"));

        if (!authService.hasPermission(user, existing, Action.UPDATE)) {
            throw new AccessDeniedException("No permission to update this resource.");
        }

        // Only non-critical fields modifiable via PATCH (null = unchanged)
        if (patch.getName() != null) existing.setName(patch.getName());
        if (patch.getDescription() != null) existing.setDescription(patch.getDescription());
        if (patch.getIp() != null) existing.setIp(patch.getIp());
        if (patch.getIp() != null) existing.setIp(patch.getIp());
        if (patch.getPort() != null) existing.setPort(patch.getPort());
        if (patch.getMaxSlots() != null) existing.setMaxSlots(patch.getMaxSlots());
        if (patch.getStationary() != null) existing.setStationary(patch.getStationary());
        if (patch.getRemote() != null) existing.setRemote(patch.getRemote());

        // MQTT fields
        if (patch.getMqttResource() != null) existing.setMqttResource(patch.getMqttResource());
        if (patch.getMqttOnline() != null) existing.setMqttOnline(patch.getMqttOnline());
        if (patch.getMqttUUID() != null) existing.setMqttUUID(patch.getMqttUUID());
        if (patch.getMqttDataReceiveTopic() != null) existing.setMqttDataReceiveTopic(patch.getMqttDataReceiveTopic());
        if (patch.getMqttDataSendTopic() != null) existing.setMqttDataSendTopic(patch.getMqttDataSendTopic());

        // Relationships (department, resourceGroup), reservations, skills are NOT modifiable via PATCH!
        // Dedicated endpoints exist for that (createResource, reserveResource, assignSkillToResource).

        return resourceRepository.save(existing);
    }

    /**
     * Validates whether a resource belongs to the given resource group.
     * Throws EntityNotFoundException if the resource or group does not exist,
     * IllegalArgumentException if the resource does not belong to the group.
     *
     * @param resourceId ID of the resource
     * @param groupId    ID of the resource group
     */
    @Transactional(readOnly = true)
    public void validateResourceInGroup(Long resourceId, Long groupId) {
        ResourceGroup group = resourceGroupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Resource group with id " + groupId + " not found"));

        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new EntityNotFoundException("Resource with id " + resourceId + " not found"));

        boolean belongs = resourceRepository.findByResourceGroup_Id(groupId)
                .stream().anyMatch(r -> r.getId().equals(resourceId));
        if (!belongs) {
            throw new IllegalArgumentException(
                    "Resource " + resourceId + " does not belong to group " + groupId + ".");
        }
    }

    /**
     * Sets the online/offline status of an MQTT resource by its MQTT UUID.
     * Called by the inbound path (device → system, STATUS message). Additionally
     * updates the last-ping timestamp. Unknown UUIDs are ignored (logged),
     * since devices register themselves via discovery first.
     *
     * @param mqttUuid the MQTT UUID of the reporting device
     * @param online   new online state
     */
    public void setMqttResourceStatusByUuid(String mqttUuid, boolean online) {
        resourceRepository.findByMqttUUID(mqttUuid).ifPresentOrElse(resource -> {
            resource.setMqttOnline(online);
            resource.setMqttLastPing(java.time.LocalDateTime.now());
            resourceRepository.save(resource);
        }, () -> log.warn("STATUS for unknown MQTT UUID '{}' ignored.", mqttUuid));
    }

    /**
     * Records a telemetry value reported by an MQTT device (VALUE message). The value is
     * appended to the named data point's comma-separated history ({@link NameValueEntry}),
     * which is created on first sight. Only the most recent {@link #MAX_VALUE_HISTORY}
     * readings are kept. Also refreshes the last-ping timestamp. Unknown UUIDs are ignored
     * (logged), like STATUS, since devices register themselves via discovery first.
     *
     * @param mqttUuid the MQTT UUID of the reporting device
     * @param name     the data point name (e.g. {@code temp})
     * @param value    the reported value
     */
    public void recordMqttValueByUuid(String mqttUuid, String name, String value) {
        resourceRepository.findByMqttUUID(mqttUuid).ifPresentOrElse(resource -> {
            appendValue(resource, name, value);
            resourceRepository.save(resource);
            log.debug("VALUE recorded for resource (uuid={}): {}={}", mqttUuid, name, value);
        }, () -> log.warn("VALUE for unknown MQTT UUID '{}' ignored.", mqttUuid));
    }

    /**
     * Records a telemetry value for a resource addressed by its numeric id. This is the REST
     * ingest path, used by devices that do not speak MQTT (and by manual/Bruno calls). Unlike
     * {@link #recordMqttValueByUuid(String, String, String)} it enforces {@link Action#UPDATE}
     * permission on the resource; the history handling is identical.
     *
     * @param resourceId the id of the target resource
     * @param name       the data point name (e.g. {@code temp})
     * @param value      the reported value
     * @param user       the authenticated user performing the ingest
     * @return the updated resource
     * @throws NoSuchElementException if no resource with the given id exists
     * @throws AccessDeniedException  if the user lacks UPDATE permission on the resource
     */
    public Resource recordMqttValue(Long resourceId, String name, String value, PUser user) {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new NoSuchElementException("Resource not found"));

        if (!authService.hasPermission(user, resource, Action.UPDATE)) {
            throw new AccessDeniedException("No update permission for this resource.");
        }

        appendValue(resource, name, value);
        Resource saved = resourceRepository.save(resource);
        log.debug("VALUE recorded via REST for resource (id={}): {}={}", resourceId, name, value);
        return saved;
    }

    /**
     * Appends {@code value} to the named data point of {@code resource}, creating the
     * {@link NameValueEntry} on first sight, and refreshes the last-ping timestamp. Shared by
     * the MQTT (by-uuid) and REST (by-id) telemetry ingest paths.
     */
    private void appendValue(Resource resource, String name, String value) {
        if (resource.getMqttValues() == null) {
            resource.setMqttValues(new java.util.HashSet<>());
        }
        NameValueEntry entry = resource.getMqttValues().stream()
                .filter(e -> name.equals(e.getMqttName()))
                .findFirst()
                .orElseGet(() -> {
                    NameValueEntry created = new NameValueEntry();
                    created.setMqttName(name);
                    resource.getMqttValues().add(created);
                    return created;
                });
        entry.setMqttValues(appendCapped(entry.getMqttValues(), value));
        resource.setMqttLastPing(java.time.LocalDateTime.now());
    }

    /**
     * Appends {@code value} to a comma-separated history, keeping only the most recent
     * {@link #MAX_VALUE_HISTORY} entries.
     */
    private static String appendCapped(String history, String value) {
        if (history == null || history.isBlank()) {
            return value;
        }
        java.util.List<String> values = new java.util.ArrayList<>(
                java.util.Arrays.asList(history.split(",")));
        values.add(value);
        if (values.size() > MAX_VALUE_HISTORY) {
            values = values.subList(values.size() - MAX_VALUE_HISTORY, values.size());
        }
        return String.join(",", values);
    }


}