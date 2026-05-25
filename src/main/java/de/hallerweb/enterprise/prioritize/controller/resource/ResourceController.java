package de.hallerweb.enterprise.prioritize.controller.resource;

import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceReservation;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.skill.SkillRecord;
import de.hallerweb.enterprise.prioritize.service.company.DepartmentService;
import de.hallerweb.enterprise.prioritize.service.resource.ResourceService;
import de.hallerweb.enterprise.prioritize.service.security.UserService; // Passe den Package-Pfad an
import de.hallerweb.enterprise.prioritize.service.skill.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;
    private final UserService userService;
    private final DepartmentService departmentService;
    private final SkillService skillService;

    /**
     * Hilfsmethode, um den aktuell authentifizierten Benutzer zu ermitteln.
     */
    private PUser getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        PUser user = userService.findUserByUsername(username);
        if (user == null) {
            throw new NoSuchElementException("Aktueller Systembenutzer nicht gefunden.");
        }
        return user;
    }


    /**
     * Liefert alle Ressourcen einer bestimmten Ressourcengruppe.
     *
     * @param groupId ID der Ressourcengruppe
     * @return ResponseEntity mit dem Satz von Ressourcen oder 404 Not Found, wenn die Gruppe nicht gefunden wurde
     */
    @GetMapping("/resourcegroups/{groupId}/resources")
    public ResponseEntity<Set<Resource>> getResourcesByResourceGroup(@PathVariable int groupId) {
        Set<Resource> resources = resourceService.getResourcesByGroupId(groupId);
        return ResponseEntity.ok(resources);
    }


    /**
     * Erstellt eine neue Ressourcengruppe für einen bestimmten Abteilung.
     *
     * @param deptId ID der Abteilung
     * @param name   Name der neuen Ressourcengruppe
     * @return ResponseEntity mit der neu erstellten Ressourcengruppe oder 404 Not Found, wenn die Abteilung nicht gefunden wurde
     */
    @PostMapping("/departments/{deptId}/resourcegroups")
    public ResponseEntity<ResourceGroup> createResourceGroup(
            @PathVariable int deptId,
            @RequestParam String name) {

        Department dept = departmentService.getDepartmentById(deptId);
        ResourceGroup group = resourceService.createResourceGroup(name, dept, getCurrentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(group);
    }


    /**
     * Löscht eine Ressourcengruppe, falls der aktuelle Benutzer berechtigt ist.
     *
     * @param groupId ID der zu löschenden Ressourcengruppe
     * @return ResponseEntity ohne Body oder 404 Not Found, wenn die Gruppe nicht gefunden wurde
     */
    @DeleteMapping("/resourcegroups/{groupId}")
    public ResponseEntity<Void> deleteResourceGroup(@PathVariable int groupId) {
        resourceService.deleteResourceGroup(groupId, getCurrentUser());
        return ResponseEntity.noContent().build();
    }


    /**
     * Erstellt eine neue Ressource in einer bestimmten Ressourcengruppe, falls der aktuelle Benutzer berechtigt ist.
     *
     * @param groupId  ID der Ressourcengruppe
     * @param resource Ressource, die erstellt werden soll
     * @return ResponseEntity mit der neu erstellten Ressource oder 404 Not Found, wenn die Gruppe nicht gefunden wurde
     */
    @PostMapping("/resourcegroups/{groupId}/resources")
    public ResponseEntity<Resource> createResource(
            @PathVariable int groupId,
            @RequestBody Resource resource) {

        Resource created = resourceService.createResource(resource, groupId, getCurrentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }


    /**
     * Ruft eine Ressource ab, falls der aktuelle Benutzer berechtigt ist.
     *
     * @param id ID der Ressource
     * @return ResponseEntity mit der Ressource oder 404 Not Found, wenn die Ressource nicht gefunden wurde
     */
    @GetMapping("/resources/{id}")
    public ResponseEntity<Resource> getResource(@PathVariable int id) {
        return ResponseEntity.ok(resourceService.getResource(id, getCurrentUser()));
    }


    /**
     * Löscht eine Ressource, falls der aktuelle Benutzer berechtigt ist.
     *
     * @param id ID der zu löschenden Ressource
     * @return ResponseEntity ohne Body oder 404 Not Found, wenn die Ressource nicht gefunden wurde
     */
    @DeleteMapping("/resources/{id}")
    public ResponseEntity<Void> deleteResource(@PathVariable int id) {
        resourceService.deleteResource(id, getCurrentUser());
        return ResponseEntity.noContent().build();
    }


    /**
     * Reserviert eine Ressource für einen bestimmten Zeitraum, falls der aktuelle Benutzer berechtigt ist.
     *
     * @param id           ID der Ressource
     * @param fromIsoDate  Startdatum der Reservierung im ISO-Format (z.B. 2026-05-15T14:00:00Z)
     * @param untilIsoDate Enddatum der Reservierung im ISO-Format (z.B. 2026-05-15T16:00:00Z)
     * @return ResponseEntity mit der neuen Reservierung oder 404 Not Found, wenn die Ressource nicht gefunden wurde
     */
    @PostMapping("/resources/{id}/reserve")
    public ResponseEntity<ResourceReservation> reserveResource(
            @PathVariable int id,
            @RequestParam String fromIsoDate,
            @RequestParam String untilIsoDate) {

        // Konvertiert die ISO-Strings (z.B. 2026-05-15T14:00:00Z) in Instants
        Instant from = Instant.parse(fromIsoDate);
        Instant until = Instant.parse(untilIsoDate);

        ResourceReservation reservation = resourceService.reserveResource(id, getCurrentUser(), from, until);
        return ResponseEntity.status(HttpStatus.CREATED).body(reservation);
    }


    // ==========================================
    // SKILL RECORDS - RESSOURCEN ZUORDNUNG
    // ==========================================

    @GetMapping({"/resources/{resourceId}/skills", "/resourcegroups/{groupId}/resources/{resourceId}/skills"})
    public ResponseEntity<Set<SkillRecord>> getSkillsForResource(@PathVariable int resourceId) {
        return ResponseEntity.ok(skillService.getSkillsForResource(resourceId));
    }



    @PostMapping("/resources/{resourceId}/skills")
    public ResponseEntity<SkillRecord> assignSkillToResource(
            @PathVariable int resourceId,
            @RequestBody SkillRecord record) {
        SkillRecord assignedRecord = skillService.assignSkillToResource(resourceId, record);
        return ResponseEntity.status(HttpStatus.CREATED).body(assignedRecord);
    }
}