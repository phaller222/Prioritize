package de.hallerweb.enterprise.prioritize.controller.company;

import de.hallerweb.enterprise.prioritize.model.company.Company;
import de.hallerweb.enterprise.prioritize.service.company.CompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@Log4j2
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    public ResponseEntity<List<Company>> getAllCompanies() {
        return ResponseEntity.ok(companyService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Company> getById(@PathVariable Long id) {
        return ResponseEntity.ok(companyService.findById(id));
    }

    @PostMapping("/filter")
    public ResponseEntity<Collection<Company>> findByFilter(@RequestBody Company filter) {
        return ResponseEntity.ok(companyService.searchCompanies(filter));
    }

    @PostMapping
    public ResponseEntity<Company> create(@RequestBody Company company) {
        return ResponseEntity.status(HttpStatus.CREATED).body(companyService.createCompany(company));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id, @RequestBody Company company) {
        if (company.getMainAddress() != null && company.getMainAddress().getId() != null) {
            throw new IllegalArgumentException("Manuelle ID-Vergabe für Adressen ist nicht erlaubt.");
        }
        companyService.updateCompany(id, company);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/address/{addressId}")
    public ResponseEntity<Void> updateAddress(
        @PathVariable Long id,
        @PathVariable Long addressId) {
        companyService.updateCompanyAddress(id, addressId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        companyService.deleteCompany(id);
        return ResponseEntity.noContent().build();
    }
}