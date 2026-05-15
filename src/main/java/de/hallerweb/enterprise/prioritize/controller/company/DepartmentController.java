package de.hallerweb.enterprise.prioritize.controller.company;

import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1") // Wir starten hier generisch
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @PostMapping("/companies/{companyId}/departments")
    public ResponseEntity<Department> create(
            @PathVariable int companyId,
            @RequestBody Department department) {

        // Der Service kümmert sich darum, das Dept der Company mit ID X zuzuweisen
        Department created = departmentService.saveDepartment(department, companyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/departments/{id}")
    public ResponseEntity<Department> update(@PathVariable int id, @RequestBody Department department) {
        Department updated = departmentService.updateDepartment(id, department);
        return ResponseEntity.ok(updated);
    }


    // GET: /api/v1/companies/1/departments  All departments of a company
    @GetMapping("/companies/{companyId}/departments")
    public ResponseEntity<Iterable<Department>> getDepartmentsByCompany(@PathVariable int companyId) {
        return ResponseEntity.ok(departmentService.getDepartmentsByCompany(companyId));
    }

    // GET: /api/v1/departments/id Get a single department by id
    @GetMapping("/departments/{id}")
    public ResponseEntity<Department> getById(@PathVariable int id) {
        return ResponseEntity.ok(departmentService.getDepartmentById(id));
    }

    // DELETE: /api/v1/departments/id Delete a department by id
    @DeleteMapping("/departments/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        departmentService.deleteDepartment(id);
        return ResponseEntity.noContent().build();
    }
}