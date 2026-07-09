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

package de.hallerweb.enterprise.prioritize.service.nfc;

import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit;
import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit.NfcUnitType;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.nfc.NfcUnitRepository;
import de.hallerweb.enterprise.prioritize.repository.project.TaskRepository;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceRepository;
import de.hallerweb.enterprise.prioritize.service.project.TaskService;
import de.hallerweb.enterprise.prioritize.service.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Manages {@link NfcUnit NFC tags} mounted on {@link Resource resources} and dispatches scans.
 * <p>
 * A tag is registered on a resource by whoever may {@link Action#UPDATE update} that resource.
 * Scanning, by contrast, is a physical event available to any authenticated user; what it does
 * depends on the tag's {@link NfcUnitType type}. A {@link NfcUnitType#TIMETRACKER} tag toggles the
 * time tracking of its bound {@link Task} &mdash; the actual authorization for that (project
 * membership) and the tracking state live in {@link TaskService}/{@link Task}.
 *
 * @author peter haller
 */
@Service
@RequiredArgsConstructor
@Transactional
@Log4j2
public class NfcUnitService {

    private final NfcUnitRepository nfcUnitRepository;
    private final ResourceRepository resourceRepository;
    private final TaskRepository taskRepository;
    private final AuthorizationService authService;
    private final TaskService taskService;
    private final ApplicationEventPublisher eventPublisher;

    /** Editable tag fields, decoupling the service from HTTP DTOs. */
    public record NfcUnitData(String uuid, String name, String description,
                              NfcUnitType type, String payload) {
    }

    /** Outcome of a scan, describing what the tag triggered. */
    public record ScanResult(String uuid, NfcUnitType type, String action,
                             Long taskId, Boolean tracking, long sequenceNumber) {
    }

    /**
     * Registers a new NFC tag on a resource. Requires {@link Action#UPDATE} on the resource.
     *
     * @param resourceId the resource the tag is mounted on
     * @param data       the tag's fields ({@code uuid} and {@code type} are mandatory)
     * @param user       the requesting user
     * @return the persisted tag
     * @throws IllegalArgumentException if uuid/type is missing or the uuid is already in use
     */
    public NfcUnit registerNfcUnit(Long resourceId, NfcUnitData data, PUser user) {
        if (data.uuid() == null || data.uuid().isBlank()) {
            throw new IllegalArgumentException("uuid is required.");
        }
        if (data.type() == null) {
            throw new IllegalArgumentException("type is required.");
        }
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new NoSuchElementException("Resource not found"));
        if (!authService.hasPermission(user, resource, Action.UPDATE)) {
            throw new AccessDeniedException("No permission to manage NFC tags on this resource.");
        }
        nfcUnitRepository.findByUuid(data.uuid()).ifPresent(existing -> {
            throw new IllegalArgumentException("An NFC tag with uuid '" + data.uuid() + "' already exists.");
        });

        NfcUnit unit = NfcUnit.builder()
                .uuid(data.uuid())
                .name(data.name())
                .description(data.description())
                .type(data.type())
                .payload(data.payload())
                .resource(resource)
                .build();
        resource.getNfcUnits().add(unit); // keep the in-memory relation consistent
        NfcUnit saved = nfcUnitRepository.save(unit);
        log.info("NFC tag '{}' (type={}) registered on resource {} by '{}'.",
                saved.getUuid(), saved.getType(), resourceId, user.getUsername());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<NfcUnit> getNfcUnitsForResource(Long resourceId, PUser user) {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new NoSuchElementException("Resource not found"));
        if (!authService.hasPermission(user, resource, Action.READ)) {
            throw new AccessDeniedException("No read permission for this resource.");
        }
        return nfcUnitRepository.findByResource_Id(resourceId);
    }

    /**
     * Binds a {@link NfcUnitType#TIMETRACKER} tag to the task whose tracking it should toggle
     * (Variant 2: one tracker tag &harr; exactly one task). Requires {@link Action#UPDATE} on the
     * tag's resource.
     *
     * @param nfcUnitId the tag id
     * @param taskId    the task to bind
     * @param user      the requesting user
     * @return the updated tag
     * @throws IllegalStateException if the tag is not a TIMETRACKER
     */
    public NfcUnit bindTask(Long nfcUnitId, Long taskId, PUser user) {
        NfcUnit unit = requireManageableUnit(nfcUnitId, user);
        if (unit.getType() != NfcUnitType.TIMETRACKER) {
            throw new IllegalStateException("Only a TIMETRACKER tag can be bound to a task.");
        }
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NoSuchElementException("Task not found"));
        unit.setTask(task);
        log.info("NFC tracker '{}' bound to task {} by '{}'.", unit.getUuid(), taskId, user.getUsername());
        return unit;
    }

    /** Clears the task binding of a TIMETRACKER tag. Requires UPDATE on the tag's resource. */
    public NfcUnit unbindTask(Long nfcUnitId, PUser user) {
        NfcUnit unit = requireManageableUnit(nfcUnitId, user);
        unit.setTask(null);
        return unit;
    }

    /** Deletes an NFC tag. Requires UPDATE on the tag's resource. */
    public void deleteNfcUnit(Long nfcUnitId, PUser user) {
        NfcUnit unit = requireManageableUnit(nfcUnitId, user);
        Resource resource = unit.getResource();
        if (resource != null) {
            resource.getNfcUnits().remove(unit); // keep the in-memory relation consistent
            unit.setResource(null);
        }
        nfcUnitRepository.delete(unit);
        log.info("NFC tag '{}' deleted by '{}'.", unit.getUuid(), user.getUsername());
    }

    /**
     * Processes a scan of the tag with the given uuid. Records the scan time and dispatches by
     * type: a TIMETRACKER toggles its bound task's tracking, a COUNTER bumps its sequence number,
     * other types are merely recorded. Available to any authenticated user; the TIMETRACKER path
     * additionally enforces the task's project membership via {@link TaskService}.
     *
     * @param uuid the scanned tag's uuid
     * @param user the scanning user
     * @return what the scan triggered
     * @throws NoSuchElementException if no tag with that uuid exists
     * @throws IllegalStateException  if a TIMETRACKER tag has no task bound
     */
    public ScanResult scan(String uuid, PUser user) {
        NfcUnit unit = nfcUnitRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("No NFC tag with uuid '" + uuid + "'"));
        unit.setLastScanTime(Instant.now());

        ScanResult result = switch (unit.getType()) {
            case TIMETRACKER -> {
                Task bound = unit.getTask();
                if (bound == null) {
                    throw new IllegalStateException("NFC tracker '" + uuid + "' is not bound to a task.");
                }
                Task task = taskService.toggleTracking(bound.getId(), user);
                String action = task.isTracking() ? "TRACKING_STARTED" : "TRACKING_STOPPED";
                yield new ScanResult(uuid, unit.getType(), action,
                        task.getId(), task.isTracking(), unit.getSequenceNumber());
            }
            case COUNTER -> {
                unit.setSequenceNumber(unit.getSequenceNumber() + 1);
                yield new ScanResult(uuid, unit.getType(), "COUNTED",
                        null, null, unit.getSequenceNumber());
            }
            default -> new ScanResult(uuid, unit.getType(), "RECORDED",
                    null, null, unit.getSequenceNumber());
        };

        // Broadcast the scan (e.g. over MQTT) once the surrounding transaction commits; the
        // service itself stays transport-agnostic — see NfcScanMqttBridge.
        Resource resource = unit.getResource();
        eventPublisher.publishEvent(new NfcScannedEvent(
                result, resource != null ? resource.getId() : null,
                user.getUsername(), unit.getLastScanTime()));
        return result;
    }

    /** Loads a tag and asserts the user may manage it (UPDATE on its resource). */
    private NfcUnit requireManageableUnit(Long nfcUnitId, PUser user) {
        NfcUnit unit = nfcUnitRepository.findById(nfcUnitId)
                .orElseThrow(() -> new NoSuchElementException("NFC tag not found"));
        if (!authService.hasPermission(user, unit.getResource(), Action.UPDATE)) {
            throw new AccessDeniedException("No permission to manage this NFC tag.");
        }
        return unit;
    }
}
