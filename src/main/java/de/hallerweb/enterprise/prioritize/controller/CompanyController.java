package de.hallerweb.enterprise.prioritize.controller;

import de.hallerweb.enterprise.prioritize.model.company.Company;
import de.hallerweb.enterprise.prioritize.service.CompanyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

@RestController
public class CompanyController {

    @Autowired
    CompanyService companyService;

    @GetMapping("/api/v1/companies")
    public List<Company> getAllCompanies() {
        return companyService.findAll();
    }

    @GetMapping("/api/v1/companies/{id}")
    public Company getCompany(@PathVariable Integer id) {
        return companyService.findById(id);
    }

    @GetMapping("/api/v1/companies/filter")
    public Collection<Company> findByCompany(@RequestBody Company company) {
        return companyService.searchCompanies(company);
    }

    @PostMapping("/api/v1/companies")
    public void createCompany(@RequestBody Company company) {
        companyService.createCompany(company);
    }

    @PutMapping("/api/v1/companies/{id}")
    public ResponseEntity<String> updateCompany(@RequestBody Company company, @PathVariable Integer id) {
       if (company.getMainAddress() != null && company.getMainAddress().getId() != null) {
            return ResponseEntity.badRequest()
                    .body("Manuelle ID-Vergabe für Adressen ist nicht erlaubt.");
        }
        companyService.updateCompany(id, company);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/v1/companies/{id}")
    public void deleteCompany(@PathVariable Integer id) {
        companyService.deleteCompany(id);
    }


}
