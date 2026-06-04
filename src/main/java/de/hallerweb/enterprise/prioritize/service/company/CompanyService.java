package de.hallerweb.enterprise.prioritize.service.company;

import de.hallerweb.enterprise.prioritize.model.company.Company;
import de.hallerweb.enterprise.prioritize.model.address.Address;
import de.hallerweb.enterprise.prioritize.repository.company.CompanyRepository;
import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.security.AuthorizationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final AuthorizationService authService;

    // TODO Mandantenfähigkeit: später auf die für 'user' sichtbaren Firmen filtern
    // (statt findAll), sobald das Tenant-Konzept an Company steht.
    @Transactional(readOnly = true)
    public List<Company> findAll() {
        return companyRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Company findById(Long id, PUser user) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Company not found with id: " + id));
        if (!authService.hasPermission(user, company, Action.READ)) {
            throw new AccessDeniedException("Keine Leseberechtigung für diese Firma.");
        }
        return company;
    }

    /**
     * Interne Variante ohne Berechtigungsprüfung – nur für andere Service-Methoden,
     * die bereits eine eigene Prüfung durchgeführt haben.
     */
    @Transactional(readOnly = true)
    Company findByIdInternal(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Company not found with id: " + id));
    }

    public Collection<Company> searchCompanies(Company filter) {
        if (filter == null) return companyRepository.findAll();

        // Wir ziehen die Adressdaten sicher heraus
        Address addr = filter.getMainAddress();
        String country = (addr != null) ? addr.getCountry() : null;
        String city = (addr != null) ? addr.getCity() : null;
        String street = (addr != null) ? addr.getStreet() : null;
        String housenumber = (addr != null) ? addr.getHousenumber() : null;

        return companyRepository.findCompaniesByFilter(
                filter.getName(),
                filter.getVatNumber(),
                filter.getTaxId(),
                country,
                housenumber,
                street,
                filter.getDescription(),
                city
        );
    }


    public Company createCompany(Company company, PUser user) {
        if (!authService.hasPermission(user, company, Action.CREATE)) {
            throw new AccessDeniedException("Keine Berechtigung zum Anlegen einer Firma.");
        }
        return companyRepository.save(company);
    }

    public void updateCompany(Long id, Company companyDetails, PUser user) {
        Company company = findByIdInternal(id);
        if (!authService.hasPermission(user, company, Action.UPDATE)) {
            throw new AccessDeniedException("Keine Berechtigung zum Ändern dieser Firma.");
        }

        // Nur Basis-Daten aktualisieren, um Beziehungen nicht versehentlich zu killen
        company.setName(companyDetails.getName());
        company.setDescription(companyDetails.getDescription());
        company.setUrl(companyDetails.getUrl());
        company.setVatNumber(companyDetails.getVatNumber());
        company.setTaxId(companyDetails.getTaxId());

        // Adresse eingebettet behandeln (null = unverändert lassen).
        // Mitgeschickte Adresse aktualisiert die bestehende bzw. legt eine neue an;
        // Persistierung erfolgt per Cascade über die Company.
        if (companyDetails.getMainAddress() != null) {
            Address newAddr = companyDetails.getMainAddress();
            Address existingAddr = company.getMainAddress();
            if (existingAddr != null) {
                existingAddr.setStreet(newAddr.getStreet());
                existingAddr.setHousenumber(newAddr.getHousenumber());
                existingAddr.setFloor(newAddr.getFloor());
                existingAddr.setZipCode(newAddr.getZipCode());
                existingAddr.setCity(newAddr.getCity());
                existingAddr.setCountry(newAddr.getCountry());
                existingAddr.setPhone(newAddr.getPhone());
                existingAddr.setFax(newAddr.getFax());
                existingAddr.setMobile(newAddr.getMobile());
            } else {
                company.setMainAddress(newAddr);
            }
        }

        // companyRepository.save(company); // Oft unnötig wegen Dirty Checking innerhalb von @Transactional
    }

    public void deleteCompany(Long id, PUser user) {
        Company company = findByIdInternal(id);
        if (!authService.hasPermission(user, company, Action.DELETE)) {
            throw new AccessDeniedException("Keine Berechtigung zum Löschen dieser Firma.");
        }
        companyRepository.deleteById(id);
    }
}