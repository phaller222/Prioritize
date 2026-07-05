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

package de.hallerweb.enterprise.prioritize.service.company;

import de.hallerweb.enterprise.prioritize.model.address.Address;
import de.hallerweb.enterprise.prioritize.model.company.Company;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.address.AddressRepository;
import de.hallerweb.enterprise.prioritize.repository.company.CompanyRepository;
import de.hallerweb.enterprise.prioritize.repository.company.DepartmentRepository;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class DepartmentServiceTest {

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private UserService userService;

    private PUser adminUser;
    private Company testCompany;
    private Department testDept;
    private Department otherDept;

    @BeforeEach
    void setUp() {
        // Fetch admin user from the DB (created by the InitializationService)
        adminUser = userService.findUserByUsername("admin");

        testCompany = Company.builder()
                .name("Dept-Test GmbH")
                .build();
        testCompany = companyRepository.save(testCompany);

        testDept = Department.builder()
                .name("Engineering-Test")
                .description("Entwicklungsabteilung")
                .build();
        testDept = departmentService.saveDepartment(testDept, testCompany.getId(), adminUser);

        otherDept = Department.builder()
                .name("Marketing-Test")
                .description("Marketingabteilung")
                .build();
        otherDept = departmentService.saveDepartment(otherDept, testCompany.getId(), adminUser);
    }

    // ==========================================
    // getDepartmentById
    // ==========================================

    @Test
    @DisplayName("getDepartmentById: Existierendes Department wird korrekt zurückgegeben")
    void getDepartmentById_ShouldReturnDepartment() {
        Department found = departmentService.getDepartmentById(testDept.getId());
        assertNotNull(found);
        assertEquals("Engineering-Test", found.getName());
    }

    @Test
    @DisplayName("getDepartmentById: Unbekannte ID wirft EntityNotFoundException")
    void getDepartmentById_UnknownId_ShouldThrow() {
        assertThrows(EntityNotFoundException.class,
                () -> departmentService.getDepartmentById(-999L));
    }

    // ==========================================
    // getDepartmentsByCompany
    // ==========================================

    @Test
    @DisplayName("getDepartmentsByCompany: Gibt alle Departments der Company zurück")
    void getDepartmentsByCompany_ShouldReturnAll() {
        List<Department> result = departmentService.getDepartmentsByCompany(testCompany.getId(), adminUser);
        assertTrue(result.stream().anyMatch(d -> d.getId().equals(testDept.getId())));
        assertTrue(result.stream().anyMatch(d -> d.getId().equals(otherDept.getId())));
    }

    // ==========================================
    // getDepartmentByName
    // ==========================================

    @Test
    @DisplayName("getDepartmentByName: Findet Department anhand des Namens")
    void getDepartmentByName_ShouldReturnDepartment() {
        Department found = departmentService.getDepartmentByName("Engineering-Test");
        assertNotNull(found);
        assertEquals(testDept.getId(), found.getId());
    }

    @Test
    @DisplayName("getDepartmentByName: Unbekannter Name wirft EntityNotFoundException")
    void getDepartmentByName_UnknownName_ShouldThrow() {
        assertThrows(EntityNotFoundException.class,
                () -> departmentService.getDepartmentByName("GibtsNicht-Test"));
    }

    // ==========================================
    // searchDepartments
    // ==========================================

    @Test
    @DisplayName("searchDepartments: Findet Departments anhand eines Teilstrings (case-insensitive)")
    void searchDepartments_ShouldReturnMatches() {
        List<Department> result = departmentService.searchDepartments("engineering");
        assertTrue(result.stream().anyMatch(d -> d.getId().equals(testDept.getId())));
        assertTrue(result.stream().noneMatch(d -> d.getId().equals(otherDept.getId())));
    }

    // ==========================================
    // saveDepartment
    // ==========================================

    @Test
    @DisplayName("saveDepartment: Department wird korrekt persistiert und der Company zugeordnet")
    void saveDepartment_ShouldPersistAndLinkToCompany() {
        Department newDept = Department.builder()
                .name("Finance-Test")
                .description("Finanzabteilung")
                .build();

        Department saved = departmentService.saveDepartment(newDept, testCompany.getId(), adminUser);

        assertNotNull(saved.getId());
        assertEquals("Finance-Test", saved.getName());
        assertEquals(testCompany.getId(), saved.getCompany().getId());
        assertTrue(departmentRepository.existsById(saved.getId()));
    }

    @Test
    @DisplayName("saveDepartment: Unbekannte Company-ID wirft EntityNotFoundException")
    void saveDepartment_UnknownCompany_ShouldThrow() {
        Department newDept = Department.builder().name("Ghost-Test").build();
        assertThrows(EntityNotFoundException.class,
                () -> departmentService.saveDepartment(newDept, -999L, adminUser));
    }

    // ==========================================
    // updateDepartment
    // ==========================================

    @Test
    @DisplayName("updateDepartment: Name und Description werden korrekt aktualisiert")
    void updateDepartment_ShouldUpdateFields() {
        Department update = Department.builder()
                .name("Engineering-Test (neu)")
                .description("Aktualisierte Beschreibung")
                .build();

        Department updated = departmentService.updateDepartment(testDept.getId(), update, adminUser);

        assertEquals("Engineering-Test (neu)", updated.getName());
        assertEquals("Aktualisierte Beschreibung", updated.getDescription());
    }

    @Test
    @DisplayName("updateDepartment: Adresse wird neu gesetzt wenn noch keine vorhanden")
    void updateDepartment_ShouldSetNewAddress() {
        Address newAddr = Address.builder()
                .street("Technikstraße")
                .housenumber("5")
                .zipCode("80333")
                .city("München")
                .country("Deutschland")
                .build();

        Department update = Department.builder()
                .name(testDept.getName())
                .description(testDept.getDescription())
                .address(newAddr)
                .build();

        Department updated = departmentService.updateDepartment(testDept.getId(), update, adminUser);

        assertNotNull(updated.getAddress());
        assertEquals("München", updated.getAddress().getCity());
    }

    @Test
    @DisplayName("updateDepartment: Bestehende Adresse wird in-place aktualisiert")
    void updateDepartment_ShouldUpdateExistingAddress() {
        Address initialAddr = Address.builder()
                .street("Altstraße")
                .housenumber("1")
                .zipCode("10000")
                .city("Berlin")
                .country("Deutschland")
                .build();
        Department withAddr = Department.builder()
                .name(testDept.getName())
                .description(testDept.getDescription())
                .address(initialAddr)
                .build();
        departmentService.updateDepartment(testDept.getId(), withAddr, adminUser);

        Address updatedAddr = Address.builder()
                .street("Neustraße")
                .housenumber("99")
                .zipCode("20000")
                .city("Hamburg")
                .country("Deutschland")
                .build();
        Department update = Department.builder()
                .name(testDept.getName())
                .description(testDept.getDescription())
                .address(updatedAddr)
                .build();

        Department updated = departmentService.updateDepartment(testDept.getId(), update, adminUser);

        assertEquals("Hamburg", updated.getAddress().getCity());
        assertEquals("Neustraße", updated.getAddress().getStreet());
    }

    @Test
    @DisplayName("updateDepartment: Unbekannte ID wirft EntityNotFoundException")
    void updateDepartment_UnknownId_ShouldThrow() {
        Department update = Department.builder().name("Ghost-Test").build();
        assertThrows(EntityNotFoundException.class,
                () -> departmentService.updateDepartment(-999L, update, adminUser));
    }

    // ==========================================
    // renameDepartment
    // ==========================================

    @Test
    @DisplayName("renameDepartment: Name wird korrekt geändert")
    void renameDepartment_ShouldUpdateName() {
        departmentService.renameDepartment(testDept.getId(), "Engineering-Test-Renamed");
        Department updated = departmentRepository.findById(testDept.getId()).orElseThrow();
        assertEquals("Engineering-Test-Renamed", updated.getName());
    }

    // ==========================================
    // deleteDepartment
    // ==========================================

    @Test
    @DisplayName("deleteDepartment: Department wird aus der DB entfernt")
    void deleteDepartment_ShouldRemoveFromDb() {
        Long id = otherDept.getId();
        departmentService.deleteDepartment(id, adminUser);
        assertThrows(EntityNotFoundException.class,
                () -> departmentService.getDepartmentById(id));
    }

    @Test
    @DisplayName("deleteDepartment: Unbekannte ID wirft EntityNotFoundException")
    void deleteDepartment_UnknownId_ShouldThrow() {
        assertThrows(EntityNotFoundException.class,
                () -> departmentService.deleteDepartment(-999L, adminUser));
    }
}