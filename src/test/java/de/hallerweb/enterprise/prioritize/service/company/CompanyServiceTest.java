package de.hallerweb.enterprise.prioritize.service.company;

import de.hallerweb.enterprise.prioritize.model.address.Address;
import de.hallerweb.enterprise.prioritize.model.company.Company;
import de.hallerweb.enterprise.prioritize.repository.address.AddressRepository;
import de.hallerweb.enterprise.prioritize.repository.company.CompanyRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class CompanyServiceTest {

    @Autowired
    private CompanyService companyService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private AddressRepository addressRepository;

    private Company acme;
    private Company globex;
    private Address acmeAddress;

    @BeforeEach
    void setUp() {
        acmeAddress = Address.builder()
                .street("Hauptstraße")
                .housenumber("1")
                .zipCode("10115")
                .city("Berlin")
                .country("Deutschland")
                .build();
        acmeAddress = addressRepository.save(acmeAddress);

        acme = Company.builder()
                .name("Acme-Test GmbH")
                .description("Testfirma Berlin")
                .url("https://acme-test.de")
                .vatNumber("DE123456789")
                .taxId("TAX-001")
                .mainAddress(acmeAddress)
                .build();
        acme = companyRepository.save(acme);

        globex = Company.builder()
                .name("Globex-Test AG")
                .description("Testfirma Hamburg")
                .vatNumber("DE987654321")
                .build();
        globex = companyRepository.save(globex);
    }

    // ==========================================
    // findAll
    // ==========================================

    @Test
    @DisplayName("findAll: Gibt mindestens die angelegten Testfirmen zurück")
    void findAll_ShouldContainTestCompanies() {
        List<Company> all = companyService.findAll();
        assertTrue(all.stream().anyMatch(c -> c.getId().equals(acme.getId())));
        assertTrue(all.stream().anyMatch(c -> c.getId().equals(globex.getId())));
    }

    // ==========================================
    // findById
    // ==========================================

    @Test
    @DisplayName("findById: Existierende Company wird korrekt zurückgegeben")
    void findById_ShouldReturnCompany() {
        Company found = companyService.findById(acme.getId());
        assertNotNull(found);
        assertEquals("Acme-Test GmbH", found.getName());
    }

    @Test
    @DisplayName("findById: Unbekannte ID wirft EntityNotFoundException")
    void findById_UnknownId_ShouldThrow() {
        assertThrows(EntityNotFoundException.class,
                () -> companyService.findById(-999L));
    }

    // ==========================================
    // searchCompanies
    // ==========================================

    @Test
    @DisplayName("searchCompanies: Findet Company anhand des Namens")
    void searchCompanies_ByName_ShouldReturnMatch() {
        Company filter = Company.builder().name("Acme-Test").build();
        Collection<Company> result = companyService.searchCompanies(filter);
        assertTrue(result.stream().anyMatch(c -> c.getId().equals(acme.getId())));
        assertTrue(result.stream().noneMatch(c -> c.getId().equals(globex.getId())));
    }

    @Test
    @DisplayName("searchCompanies: Findet Company anhand der Stadt in der Adresse")
    void searchCompanies_ByCity_ShouldReturnMatch() {
        Address addrFilter = Address.builder().city("Berlin").build();
        Company filter = Company.builder().mainAddress(addrFilter).build();
        Collection<Company> result = companyService.searchCompanies(filter);
        assertTrue(result.stream().anyMatch(c -> c.getId().equals(acme.getId())));
    }

    @Test
    @DisplayName("searchCompanies: Null-Filter gibt alle Companies zurück")
    void searchCompanies_NullFilter_ShouldReturnAll() {
        Collection<Company> result = companyService.searchCompanies(null);
        assertTrue(result.stream().anyMatch(c -> c.getId().equals(acme.getId())));
        assertTrue(result.stream().anyMatch(c -> c.getId().equals(globex.getId())));
    }

    // ==========================================
    // createCompany
    // ==========================================

    @Test
    @DisplayName("createCompany: Neue Company wird korrekt persistiert")
    void createCompany_ShouldPersist() {
        Company newCompany = Company.builder()
                .name("Initech-Test GmbH")
                .description("Dritte Testfirma")
                .vatNumber("DE111222333")
                .build();

        Company created = companyService.createCompany(newCompany);

        assertNotNull(created.getId());
        assertEquals("Initech-Test GmbH", created.getName());
        assertTrue(companyRepository.existsById(created.getId()));
    }

    // ==========================================
    // updateCompany
    // ==========================================

    @Test
    @DisplayName("updateCompany: Basisfelder werden korrekt aktualisiert")
    void updateCompany_ShouldUpdateFields() {
        Company update = Company.builder()
                .name("Acme-Test GmbH (neu)")
                .description("Aktualisierte Beschreibung")
                .url("https://acme-test-neu.de")
                .vatNumber("DE000000000")
                .taxId("TAX-999")
                .build();

        companyService.updateCompany(acme.getId(), update);

        Company updated = companyRepository.findById(acme.getId()).orElseThrow();
        assertEquals("Acme-Test GmbH (neu)", updated.getName());
        assertEquals("Aktualisierte Beschreibung", updated.getDescription());
        assertEquals("https://acme-test-neu.de", updated.getUrl());
        assertEquals("DE000000000", updated.getVatNumber());
        assertEquals("TAX-999", updated.getTaxId());
    }

    @Test
    @DisplayName("updateCompany: Unbekannte ID wirft EntityNotFoundException")
    void updateCompany_UnknownId_ShouldThrow() {
        Company update = Company.builder().name("Ghost-Test").build();
        assertThrows(EntityNotFoundException.class,
                () -> companyService.updateCompany(-999L, update));
    }

    // ==========================================
    // updateCompanyAddress
    // ==========================================

    @Test
    @DisplayName("updateCompanyAddress: Adresse wird korrekt gewechselt")
    void updateCompanyAddress_ShouldUpdateAddress() {
        Address newAddress = Address.builder()
                .street("Reeperbahn")
                .housenumber("1")
                .zipCode("20359")
                .city("Hamburg")
                .country("Deutschland")
                .build();
        newAddress = addressRepository.save(newAddress);

        companyService.updateCompanyAddress(acme.getId(), newAddress.getId());

        Company updated = companyRepository.findById(acme.getId()).orElseThrow();
        assertEquals("Hamburg", updated.getMainAddress().getCity());
    }

    @Test
    @DisplayName("updateCompanyAddress: Unbekannte Company-ID wirft EntityNotFoundException")
    void updateCompanyAddress_UnknownCompany_ShouldThrow() {
        assertThrows(EntityNotFoundException.class,
                () -> companyService.updateCompanyAddress(-999L, acmeAddress.getId()));
    }

    @Test
    @DisplayName("updateCompanyAddress: Unbekannte Address-ID wirft EntityNotFoundException")
    void updateCompanyAddress_UnknownAddress_ShouldThrow() {
        assertThrows(EntityNotFoundException.class,
                () -> companyService.updateCompanyAddress(acme.getId(), -999L));
    }

    // ==========================================
    // deleteCompany
    // ==========================================

    @Test
    @DisplayName("deleteCompany: Company wird aus der DB entfernt")
    void deleteCompany_ShouldRemoveFromDb() {
        Long id = globex.getId();
        companyService.deleteCompany(id);
        assertFalse(companyRepository.existsById(id));
    }

    @Test
    @DisplayName("deleteCompany: Unbekannte ID wirft EntityNotFoundException")
    void deleteCompany_UnknownId_ShouldThrow() {
        assertThrows(EntityNotFoundException.class,
                () -> companyService.deleteCompany(-999L));
    }
}