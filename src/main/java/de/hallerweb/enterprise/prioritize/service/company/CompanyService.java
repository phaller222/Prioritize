package de.hallerweb.enterprise.prioritize.service.company;

import de.hallerweb.enterprise.prioritize.model.company.Company;
import de.hallerweb.enterprise.prioritize.model.company.Address;
import de.hallerweb.enterprise.prioritize.repository.company.CompanyRepository;
import de.hallerweb.enterprise.prioritize.repository.address.AddressRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final AddressRepository addressRepository;

    @Transactional(readOnly = true)
    public List<Company> findAll() {
        return companyRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Company findById(Long id) {
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


    public Company createCompany(Company company) {
        return  companyRepository.save(company);
    }

    public void updateCompany(Long id, Company companyDetails) {
        Company company = findById(id);

        // Nur Basis-Daten aktualisieren, um Beziehungen nicht versehentlich zu killen
        company.setName(companyDetails.getName());
        company.setDescription(companyDetails.getDescription());
        company.setUrl(companyDetails.getUrl());
        company.setVatNumber(companyDetails.getVatNumber());
        company.setTaxId(companyDetails.getTaxId());

        // companyRepository.save(company); // Oft unnötig wegen Dirty Checking innerhalb von @Transactional
    }

    public void deleteCompany(Long id) {
        if (!companyRepository.existsById(id)) {
            throw new EntityNotFoundException("Cannot delete. Company not found.");
        }
        companyRepository.deleteById(id);
    }

    public void updateCompanyAddress(Long companyId, Long newAddressId) {
        Company company = findById(companyId);
        Address newAddress = addressRepository.findById(newAddressId)
                .orElseThrow(() -> new EntityNotFoundException("Address not found"));

        company.setMainAddress(newAddress);
    }
}