package de.hallerweb.enterprise.prioritize.controller.company;

import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.service.company.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @PostMapping("/companies/{companyId}/departments")
    public ResponseEntity<Department> create(
        @PathVariable Long companyId,
        @RequestBody Department department) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(departmentService.saveDepartment(department, companyId));
    }

    @GetMapping("/companies/{companyId}/departments")
    public ResponseEntity<List<Department>> getDepartmentsByCompany(@PathVariable Long companyId) {
        return ResponseEntity.ok(departmentService.getDepartmentsByCompany(companyId));
    }

    @GetMapping("/departments/{id}")
    public ResponseEntity<Department> getById(@PathVariable Long id) {
        return ResponseEntity.ok(departmentService.getDepartmentById(id));
    }

    @PutMapping("/departments/{id}")
    public ResponseEntity<Department> update(
        @PathVariable Long id,
        @RequestBody Department department) {
        return ResponseEntity.ok(departmentService.updateDepartment(id, department));
    }

    @DeleteMapping("/departments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return ResponseEntity.noContent().build();
    }
}