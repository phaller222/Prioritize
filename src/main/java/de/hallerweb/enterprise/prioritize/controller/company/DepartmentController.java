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

package de.hallerweb.enterprise.prioritize.controller.company;

import de.hallerweb.enterprise.prioritize.config.AuthenticatedUser;
import de.hallerweb.enterprise.prioritize.dto.company.DepartmentDTO;
import de.hallerweb.enterprise.prioritize.dto.company.DepartmentRequest;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.company.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

@Tag(name = "Departments", description = "Manage departments within a company.")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @Operation(summary = "Create department")
    @PostMapping("/companies/{companyId}/departments")
    public ResponseEntity<DepartmentDTO> create(
            @PathVariable Long companyId,
            @RequestBody DepartmentRequest request,
            @AuthenticatedUser PUser currentUser) {
        Department created = departmentService.saveDepartment(request.toDepartment(), companyId, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(DepartmentDTO.from(created));
    }

    @Operation(summary = "Get departments by company")
    @GetMapping("/companies/{companyId}/departments")
    public ResponseEntity<List<DepartmentDTO>> getDepartmentsByCompany(
            @PathVariable Long companyId,
            @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(departmentService.getDepartmentsByCompany(companyId, currentUser)
                .stream().map(DepartmentDTO::from).toList());
    }

    /**
     * Flat listing across all companies, for clients that do not have a company id to start from —
     * see {@link DepartmentService#getReadableDepartments} for why that is the normal case.
     */
    @Operation(summary = "List the departments the caller may read, across all companies")
    @GetMapping("/departments")
    public ResponseEntity<List<DepartmentDTO>> getDepartments(@AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(departmentService.getReadableDepartments(currentUser)
                .stream().map(DepartmentDTO::from).toList());
    }

    @Operation(summary = "Get department by id")
    @GetMapping("/departments/{id}")
    public ResponseEntity<DepartmentDTO> getById(@PathVariable Long id, @AuthenticatedUser PUser currentUser) {
        return ResponseEntity.ok(DepartmentDTO.from(departmentService.getDepartment(id, currentUser)));
    }

    @Operation(summary = "Update department")
    @PutMapping("/departments/{id}")
    public ResponseEntity<DepartmentDTO> update(
            @PathVariable Long id,
            @RequestBody DepartmentRequest request,
            @AuthenticatedUser PUser currentUser) {
        Department updated = departmentService.updateDepartment(id, request.toDepartment(), currentUser);
        return ResponseEntity.ok(DepartmentDTO.from(updated));
    }

    @Operation(summary = "Delete department")
    @DeleteMapping("/departments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticatedUser PUser currentUser) {
        departmentService.deleteDepartment(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}