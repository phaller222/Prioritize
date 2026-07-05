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
import de.hallerweb.enterprise.prioritize.model.company.Company;
import de.hallerweb.enterprise.prioritize.service.company.CompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@Log4j2
public class CompanyController {

    private final CompanyService companyService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ResponseEntity<List<Company>> getAllCompanies() {
        return ResponseEntity.ok(companyService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Company> getById(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(companyService.findById(id, currentUserResolver.resolve(auth)));
    }

    @PostMapping("/filter")
    public ResponseEntity<Collection<Company>> findByFilter(@RequestBody Company filter) {
        return ResponseEntity.ok(companyService.searchCompanies(filter));
    }

    @PostMapping
    public ResponseEntity<Company> create(@RequestBody Company company, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(companyService.createCompany(company, currentUserResolver.resolve(auth)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id, @RequestBody Company company, Authentication auth) {
        if (company.getMainAddress() != null && company.getMainAddress().getId() != null) {
            throw new IllegalArgumentException("Manual ID assignment for addresses is not allowed.");
        }
        companyService.updateCompany(id, company, currentUserResolver.resolve(auth));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        companyService.deleteCompany(id, currentUserResolver.resolve(auth));
        return ResponseEntity.noContent().build();
    }
}