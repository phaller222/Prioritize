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

import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.company.DepartmentService;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;
    private final UserService userService;

    private PUser currentUser(Authentication auth) {
        return userService.findUserByUsername(auth.getName());
    }

    @PostMapping("/companies/{companyId}/departments")
    public ResponseEntity<Department> create(
            @PathVariable Long companyId,
            @RequestBody Department department,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(departmentService.saveDepartment(department, companyId, currentUser(auth)));
    }

    @GetMapping("/companies/{companyId}/departments")
    public ResponseEntity<List<Department>> getDepartmentsByCompany(
            @PathVariable Long companyId,
            Authentication auth) {
        return ResponseEntity.ok(departmentService.getDepartmentsByCompany(companyId, currentUser(auth)));
    }

    @GetMapping("/departments/{id}")
    public ResponseEntity<Department> getById(@PathVariable Long id) {
        return ResponseEntity.ok(departmentService.getDepartmentById(id));
    }

    @PutMapping("/departments/{id}")
    public ResponseEntity<Department> update(
            @PathVariable Long id,
            @RequestBody Department department,
            Authentication auth) {
        return ResponseEntity.ok(departmentService.updateDepartment(id, department, currentUser(auth)));
    }

    @DeleteMapping("/departments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        departmentService.deleteDepartment(id, currentUser(auth));
        return ResponseEntity.noContent().build();
    }
}