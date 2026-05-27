package de.hallerweb.enterprise.prioritize.controller.company;

import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.company.DepartmentService;
import de.hallerweb.enterprise.prioritize.service.security.AuthorizationService;
import de.hallerweb.enterprise.prioritize.service.security.UserService; // HIERZUGEFÜGT
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;
    private final AuthorizationService authService;
    private final UserService userService; // HIERZUGEFÜGT

    @PostMapping("/companies/{companyId}/departments")
    public ResponseEntity<Department> create(
            @PathVariable Long companyId,
            @RequestBody Department department) {

        PUser currentUser = userService.getCurrentUser();

        if (!authService.hasPermission(currentUser, "de.hallerweb.enterprise.prioritize.model.company.Company", companyId, Action.CREATE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Department created = departmentService.saveDepartment(department, companyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/companies/{companyId}/departments")
    public ResponseEntity<Iterable<Department>> getDepartmentsByCompany(@PathVariable Long companyId) {

        PUser currentUser = userService.getCurrentUser();

        if (!authService.hasPermission(currentUser, "de.hallerweb.enterprise.prioritize.model.company.Company", companyId, Action.READ)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(departmentService.getDepartmentsByCompany(companyId));
    }

    @PutMapping("/departments/{id}")
    public ResponseEntity<Department> update(@PathVariable Long id, @RequestBody Department department) {
        PUser currentUser = userService.getCurrentUser();

        if (!authService.hasPermission(currentUser, "de.hallerweb.enterprise.prioritize.model.company.Department", id, Action.UPDATE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Department updated = departmentService.updateDepartment(id, department);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/departments/{id}")
    public ResponseEntity<Department> getById(@PathVariable Long id) {
        PUser currentUser = userService.getCurrentUser();

        if (!authService.hasPermission(currentUser, "de.hallerweb.enterprise.prioritize.model.company.Department", id, Action.READ)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(departmentService.getDepartmentById(id));
    }

    @DeleteMapping("/departments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        PUser currentUser = userService.getCurrentUser();

        if (!authService.hasPermission(currentUser, "de.hallerweb.enterprise.prioritize.model.company.Department", id, Action.DELETE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        departmentService.deleteDepartment(id);
        return ResponseEntity.noContent().build();
    }
}