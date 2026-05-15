package de.hallerweb.enterprise.prioritize.controller.resource;

import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceReservation;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.DepartmentService;
import de.hallerweb.enterprise.prioritize.service.resource.ResourceService;
import de.hallerweb.enterprise.prioritize.service.security.UserService; // Passe den Package-Pfad an
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

    // --- Ressourcengruppen verwalten ---

    @GetMapping("/resourcegroups/{groupId}/resources")
    public ResponseEntity<Set<Resource>> getResourcesByResourceGroup(@PathVariable int groupId) {
        Set<Resource> resources = resourceService.getResourcesByGroupId(groupId);
        return ResponseEntity.ok(resources);
    }

    @PostMapping("/departments/{deptId}/resourcegroups")
    public ResponseEntity<ResourceGroup> createResourceGroup(
            @PathVariable int deptId,
            @RequestParam String name) {

        Department dept = departmentService.getDepartmentById(deptId);
        ResourceGroup group = resourceService.createResourceGroup(name, dept, getCurrentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(group);
    }

    @DeleteMapping("/resourcegroups/{groupId}")
    public ResponseEntity<Void> deleteResourceGroup(@PathVariable int groupId) {
        resourceService.deleteResourceGroup(groupId, getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    // --- Ressourcen verwalten ---

    @PostMapping("/resourcegroups/{groupId}/resources")
    public ResponseEntity<Resource> createResource(
            @PathVariable int groupId,
            @RequestBody Resource resource) {

        Resource created = resourceService.createResource(resource, groupId, getCurrentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/resources/{id}")
    public ResponseEntity<Resource> getResource(@PathVariable int id) {
        return ResponseEntity.ok(resourceService.getResource(id, getCurrentUser()));
    }

    @DeleteMapping("/resources/{id}")
    public ResponseEntity<Void> deleteResource(@PathVariable int id) {
        resourceService.deleteResource(id, getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    // --- Reservierungen vornehmen ---

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
}