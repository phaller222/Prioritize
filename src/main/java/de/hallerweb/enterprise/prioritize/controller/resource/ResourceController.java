package de.hallerweb.enterprise.prioritize.controller.resource;

import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceReservation;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.skill.SkillRecord;
import de.hallerweb.enterprise.prioritize.config.CurrentUserResolver;
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
     * Hilfsmethode, um den aktuell authentifizierten Benutzer zu ermitteln.
     */
    private PUser getCurrentUser(Authentication auth) {
        return currentUserResolver.resolve(auth);
    }


    /**
     * Liefert alle Ressourcen einer bestimmten Ressourcengruppe.
     *
     * @param groupId ID der Ressourcengruppe
     * @return ResponseEntity mit dem Satz von Ressourcen
     */
    @GetMapping("/resourcegroups/{groupId}/resources")
    public ResponseEntity<Set<Resource>> getResourcesByResourceGroup(@PathVariable Long groupId) {
        Set<Resource> resources = resourceService.getResourcesByGroupId(groupId);
        return ResponseEntity.ok(resources);
    }


    /**
     * Erstellt eine neue Ressourcengruppe für eine bestimmte Abteilung.
     *
     * @param deptId ID der Abteilung
     * @param name   Name der neuen Ressourcengruppe
     * @return ResponseEntity mit der neu erstellten Ressourcengruppe
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
     * Löscht eine Ressourcengruppe, falls der aktuelle Benutzer berechtigt ist.
     *
     * @param groupId ID der zu löschenden Ressourcengruppe
     * @return ResponseEntity ohne Body
     */
    @DeleteMapping("/resourcegroups/{groupId}")
    public ResponseEntity<Void> deleteResourceGroup(
            @PathVariable Long groupId,
            Authentication auth) {

        resourceService.deleteResourceGroup(groupId, getCurrentUser(auth));
        return ResponseEntity.noContent().build();
    }


    /**
     * Erstellt eine neue Ressource in einer bestimmten Ressourcengruppe.
     *
     * @param groupId  ID der Ressourcengruppe
     * @param resource Ressource, die erstellt werden soll
     * @return ResponseEntity mit der neu erstellten Ressource
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
     * Ruft eine Ressource ab, falls der aktuelle Benutzer berechtigt ist.
     *
     * @param id ID der Ressource
     * @return ResponseEntity mit der Ressource
     */
    @GetMapping("/resources/{id}")
    public ResponseEntity<Resource> getResource(
            @PathVariable Long id,
            Authentication auth) {

        return ResponseEntity.ok(resourceService.getResource(id, getCurrentUser(auth)));
    }

    /**
     * Aktualisiert einzelne Felder einer Resource (PATCH-Semantik: null = unverändert).
     *
     * @param id    ID der Resource
     * @param patch Resource mit den zu ändernden Feldern
     * @return die aktualisierte Resource
     */
    @PatchMapping("/resources/{id}")
    public ResponseEntity<Resource> partialUpdateResource(
            @PathVariable Long id,
            @RequestBody Resource patch,
            Authentication auth) {

        return ResponseEntity.ok(resourceService.partialUpdateResource(id, patch, getCurrentUser(auth)));
    }

    /**
     * Löscht eine Ressource, falls der aktuelle Benutzer berechtigt ist.
     *
     * @param id ID der zu löschenden Ressource
     * @return ResponseEntity ohne Body
     */
    @DeleteMapping("/resources/{id}")
    public ResponseEntity<Void> deleteResource(
            @PathVariable Long id,
            Authentication auth) {

        resourceService.deleteResource(id, getCurrentUser(auth));
        return ResponseEntity.noContent().build();
    }


    /**
     * Reserviert eine Ressource für einen bestimmten Zeitraum.
     *
     * @param id           ID der Ressource
     * @param fromIsoDate  Startdatum im ISO-Format (z.B. 2026-05-15T14:00:00Z)
     * @param untilIsoDate Enddatum im ISO-Format (z.B. 2026-05-15T16:00:00Z)
     * @return ResponseEntity mit der neuen Reservierung
     */
    @PostMapping("/resources/{id}/reserve")
    public ResponseEntity<ResourceReservation> reserveResource(
            @PathVariable Long id,
            @RequestParam String fromIsoDate,
            @RequestParam String untilIsoDate,
            Authentication auth) {

        // IllegalArgumentException bei ungültigem Format wird vom GlobalExceptionHandler → 400 gemappt
        Instant from = Instant.parse(fromIsoDate);
        Instant until = Instant.parse(untilIsoDate);

        if (!until.isAfter(from)) {
            throw new IllegalArgumentException("Das Enddatum muss nach dem Startdatum liegen.");
        }

        ResourceReservation reservation = resourceService.reserveResource(id, getCurrentUser(auth), from, until);
        return ResponseEntity.status(HttpStatus.CREATED).body(reservation);
    }

// ==========================================
    // RESSOURCEN-STEUERUNG (Command senden)
    // ==========================================

    /**
     * Sendet ein Steuerkommando an eine Resource. Der Transport (MQTT/REST) wird
     * automatisch gewählt (MQTT bevorzugt wenn online, sonst REST-Fallback).
     *
     * @param id      ID der Resource
     * @param request Kommando und optionaler freier Parameter
     * @return 202 Accepted, wenn das Kommando übergeben wurde
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

    /** Request-Body für ein Steuerkommando. {@code param} ist optional. */
    public record ResourceCommandRequest(String command, String param) {
    }

    // ==========================================
    // SKILL RECORDS - RESSOURCEN ZUORDNUNG
    // ==========================================

    /**
     * Liefert alle Skills einer Ressource.
     *
     * @param resourceId ID der Ressource
     * @return ResponseEntity mit dem Satz von SkillRecords
     */
    @GetMapping("/resources/{resourceId}/skills")
    public ResponseEntity<Set<SkillRecord>> getSkillsForResource(@PathVariable Long resourceId) {
        return ResponseEntity.ok(skillService.getSkillsForResource(resourceId));
    }

    /**
     * Liefert alle Skills einer Ressource, gefiltert nach Ressourcengruppe.
     * Validiert zusätzlich, ob die Ressource zur angegebenen Gruppe gehört.
     *
     * @param groupId    ID der Ressourcengruppe
     * @param resourceId ID der Ressource
     * @return ResponseEntity mit dem Satz von SkillRecords
     */
    @GetMapping("/resourcegroups/{groupId}/resources/{resourceId}/skills")
    public ResponseEntity<Set<SkillRecord>> getSkillsForResourceInGroup(
            @PathVariable Long groupId,
            @PathVariable Long resourceId) {

        resourceService.validateResourceInGroup(resourceId, groupId);
        return ResponseEntity.ok(skillService.getSkillsForResource(resourceId));
    }

    /**
     * Weist einer Ressource einen Skill zu.
     *
     * @param resourceId ID der Ressource
     * @param record     Der zuzuweisende SkillRecord
     * @return ResponseEntity mit dem erstellten SkillRecord
     */
    @PostMapping("/resources/{resourceId}/skills")
    public ResponseEntity<SkillRecord> assignSkillToResource(
            @PathVariable Long resourceId,
            @RequestBody SkillRecord record) {

        SkillRecord assignedRecord = skillService.assignSkillToResource(resourceId, record);
        return ResponseEntity.status(HttpStatus.CREATED).body(assignedRecord);
    }
}