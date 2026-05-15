package de.hallerweb.enterprise.prioritize.controller.company;

import de.hallerweb.enterprise.prioritize.model.company.Company;
import de.hallerweb.enterprise.prioritize.service.CompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
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
    public List<Company> getAllCompanies() {
        return companyService.findAll();
    }

    @GetMapping("/{id}")
    public Company get(@PathVariable Integer id) {
        return companyService.findById(id);
    }

    @PostMapping("/filter")
    public Collection<Company> findBy(@RequestBody Company company) {
        return companyService.searchCompanies(company);
    }

    @PostMapping // Das leere Mapping für "POST /api/v1/companies"
    public ResponseEntity<Company> create(@RequestBody Company company) {
        Company created = companyService.createCompany(company);
        return ResponseEntity.status(201).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> update(@RequestBody Company company, @PathVariable Integer id) {
        if (company.getMainAddress() != null && company.getMainAddress().getId() != null) {
            return ResponseEntity.badRequest()
                    .body("Manuelle ID-Vergabe für Adressen ist nicht erlaubt.");
        }
        companyService.updateCompany(id, company);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        companyService.deleteCompany(id);
    }
}
