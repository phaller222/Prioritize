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
            throw new IllegalArgumentException("Manuelle ID-Vergabe für Adressen ist nicht erlaubt.");
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