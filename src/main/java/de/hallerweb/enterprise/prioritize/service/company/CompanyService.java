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

    // TODO multi-tenancy: later filter to the companies visible to 'user'
    // (instead of findAll), once the tenant concept is in place on Company.
    @Transactional(readOnly = true)
    public List<Company> findAll() {
        return companyRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Company findById(Long id, PUser user) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Company not found with id: " + id));
        if (!authService.hasPermission(user, company, Action.READ)) {
            throw new AccessDeniedException("No read permission for this company.");
        }
        return company;
    }

    /**
     * Internal variant without permission check – only for other service methods
     * that have already performed their own check.
     */
    @Transactional(readOnly = true)
    Company findByIdInternal(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Company not found with id: " + id));
    }

    public Collection<Company> searchCompanies(Company filter) {
        if (filter == null) return companyRepository.findAll();

        // We safely extract the address data
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
            throw new AccessDeniedException("No permission to create a company.");
        }
        return companyRepository.save(company);
    }

    public void updateCompany(Long id, Company companyDetails, PUser user) {
        Company company = findByIdInternal(id);
        if (!authService.hasPermission(user, company, Action.UPDATE)) {
            throw new AccessDeniedException("No permission to update this company.");
        }

        // Only update base data, so as not to accidentally kill relationships
        company.setName(companyDetails.getName());
        company.setDescription(companyDetails.getDescription());
        company.setUrl(companyDetails.getUrl());
        company.setVatNumber(companyDetails.getVatNumber());
        company.setTaxId(companyDetails.getTaxId());

        // Treat the address as embedded (null = leave unchanged).
        // A supplied address updates the existing one or creates a new one;
        // persistence happens via cascade through the Company.
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

        // companyRepository.save(company); // Often unnecessary due to dirty checking within @Transactional
    }

    public void deleteCompany(Long id, PUser user) {
        Company company = findByIdInternal(id);
        if (!authService.hasPermission(user, company, Action.DELETE)) {
            throw new AccessDeniedException("No permission to delete this company.");
        }
        companyRepository.deleteById(id);
    }
}