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

import de.hallerweb.enterprise.prioritize.config.CurrentUserResolver;
import de.hallerweb.enterprise.prioritize.dto.company.CompanyDTO;
import de.hallerweb.enterprise.prioritize.dto.company.CompanyRequest;
import de.hallerweb.enterprise.prioritize.model.company.Company;
import de.hallerweb.enterprise.prioritize.service.company.CompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

@Tag(name = "Companies", description = "Create, read, update and delete companies.")
@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@Log4j2
public class CompanyController {

    private final CompanyService companyService;
    private final CurrentUserResolver currentUserResolver;

    @Operation(summary = "Get all companies")
    @GetMapping
    public ResponseEntity<List<CompanyDTO>> getAllCompanies() {
        return ResponseEntity.ok(companyService.findAll().stream().map(CompanyDTO::from).toList());
    }

    @Operation(summary = "Get company by id")
    @GetMapping("/{id}")
    public ResponseEntity<CompanyDTO> getById(@PathVariable Long id, Authentication auth) {
        Company company = companyService.findById(id, currentUserResolver.resolve(auth));
        return ResponseEntity.ok(CompanyDTO.from(company));
    }

    @Operation(summary = "Find companies matching a filter")
    @PostMapping("/filter")
    public ResponseEntity<List<CompanyDTO>> findByFilter(@RequestBody CompanyRequest filter) {
        return ResponseEntity.ok(
                companyService.searchCompanies(filter.toCompany()).stream().map(CompanyDTO::from).toList());
    }

    @Operation(summary = "Create company")
    @PostMapping
    public ResponseEntity<CompanyDTO> create(@RequestBody CompanyRequest request, Authentication auth) {
        Company created = companyService.createCompany(request.toCompany(), currentUserResolver.resolve(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(CompanyDTO.from(created));
    }

    @Operation(summary = "Update company")
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id, @RequestBody CompanyRequest request, Authentication auth) {
        companyService.updateCompany(id, request.toCompany(), currentUserResolver.resolve(auth));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete company")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        companyService.deleteCompany(id, currentUserResolver.resolve(auth));
        return ResponseEntity.noContent().build();
    }
}