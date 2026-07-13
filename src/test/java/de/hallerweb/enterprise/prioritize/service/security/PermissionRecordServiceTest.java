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

package de.hallerweb.enterprise.prioritize.service.security;

import de.hallerweb.enterprise.prioritize.model.company.Company;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.security.PermissionRecord;
import de.hallerweb.enterprise.prioritize.repository.company.CompanyRepository;
import de.hallerweb.enterprise.prioritize.repository.company.DepartmentRepository;
import de.hallerweb.enterprise.prioritize.repository.security.PermissionRecordRepository;
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
class PermissionRecordServiceTest {

    private static final String COMPANY_TYPE = "de.hallerweb.enterprise.prioritize.model.company.Company";

    @Autowired
    private PermissionRecordService permissionService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private PermissionRecordRepository permissionRepository;

    private Department testDept;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder().name("Perm-Test GmbH").build());
        testDept = departmentRepository.save(Department.builder()
                .name("Perm-Test-Dept")
                .company(company)
                .build());
    }

    private PermissionRecord newRecord() {
        return PermissionRecord.builder()
                .absoluteObjectType(COMPANY_TYPE)
                .objectId(0L)
                .readPermission(true)
                .build();
    }

    // ==========================================
    // createPermission
    // ==========================================

    @Test
    @DisplayName("createPermission: Record wird persistiert, der Abteilung zugeordnet und objectName abgeleitet")
    void createPermission_ShouldPersistAndScope() {
        PermissionRecord saved = permissionService.createPermission(newRecord(), testDept.getId());

        assertNotNull(saved.getId());
        assertEquals(testDept.getId(), saved.getDepartment().getId());
        // @PrePersist derives the simple class name from the absolute type
        assertEquals("Company", saved.getObjectName());
        assertTrue(saved.isReadPermission());
    }

    @Test
    @DisplayName("createPermission: departmentId null erzeugt einen ungescopten Record")
    void createPermission_NullDepartment_ShouldBeUnscoped() {
        PermissionRecord saved = permissionService.createPermission(newRecord(), null);
        assertNull(saved.getDepartment());
    }

    @Test
    @DisplayName("createPermission: Unbekannte Department-ID wirft EntityNotFoundException")
    void createPermission_UnknownDepartment_ShouldThrow() {
        assertThrows(EntityNotFoundException.class,
                () -> permissionService.createPermission(newRecord(), -999L));
    }

    // ==========================================
    // getPermissionById / getAll / queries
    // ==========================================

    @Test
    @DisplayName("getPermissionById: Existierenden Record zurückgeben")
    void getPermissionById_ShouldReturnRecord() {
        PermissionRecord saved = permissionService.createPermission(newRecord(), null);
        PermissionRecord found = permissionService.getPermissionById(saved.getId());
        assertEquals(saved.getId(), found.getId());
    }

    @Test
    @DisplayName("getPermissionById: Unbekannte ID wirft EntityNotFoundException")
    void getPermissionById_UnknownId_ShouldThrow() {
        assertThrows(EntityNotFoundException.class, () -> permissionService.getPermissionById(-999L));
    }

    @Test
    @DisplayName("getPermissionsByDepartment: Gibt die Records der Abteilung zurück")
    void getPermissionsByDepartment_ShouldReturnRecords() {
        PermissionRecord saved = permissionService.createPermission(newRecord(), testDept.getId());
        List<PermissionRecord> result = permissionService.getPermissionsByDepartment(testDept.getId());
        assertTrue(result.stream().anyMatch(p -> p.getId().equals(saved.getId())));
    }

    @Test
    @DisplayName("getPermissionsByType: Findet Records anhand des absoluten Typs")
    void getPermissionsByType_ShouldReturnRecords() {
        PermissionRecord saved = permissionService.createPermission(newRecord(), null);
        List<PermissionRecord> result = permissionService.getPermissionsByType(COMPANY_TYPE);
        assertTrue(result.stream().anyMatch(p -> p.getId().equals(saved.getId())));
    }

    // ==========================================
    // updatePermission
    // ==========================================

    @Test
    @DisplayName("updatePermission: C/R/U/D-Flags und Target werden aktualisiert")
    void updatePermission_ShouldUpdateFlagsAndTarget() {
        PermissionRecord saved = permissionService.createPermission(newRecord(), null);

        PermissionRecord details = PermissionRecord.builder()
                .absoluteObjectType("de.hallerweb.enterprise.prioritize.model.company.Department")
                .objectId(42L)
                .createPermission(true)
                .readPermission(true)
                .updatePermission(true)
                .deletePermission(true)
                .build();

        PermissionRecord updated = permissionService.updatePermission(saved.getId(), details, null);
        // objectName is derived by the entity's @PreUpdate hook, which only fires on flush
        permissionRepository.flush();

        assertTrue(updated.isCreatePermission());
        assertTrue(updated.isDeletePermission());
        assertEquals(42L, updated.getObjectId());
        assertEquals("Department", updated.getObjectName());
    }

    @Test
    @DisplayName("updatePermission: Unbekannte ID wirft EntityNotFoundException")
    void updatePermission_UnknownId_ShouldThrow() {
        assertThrows(EntityNotFoundException.class,
                () -> permissionService.updatePermission(-999L, newRecord(), null));
    }

    // ==========================================
    // deletePermission
    // ==========================================

    @Test
    @DisplayName("deletePermission: Record wird entfernt")
    void deletePermission_ShouldRemove() {
        PermissionRecord saved = permissionService.createPermission(newRecord(), null);
        Long id = saved.getId();
        permissionService.deletePermission(id);
        assertThrows(EntityNotFoundException.class, () -> permissionService.getPermissionById(id));
    }

    @Test
    @DisplayName("deletePermission: Unbekannte ID wirft EntityNotFoundException")
    void deletePermission_UnknownId_ShouldThrow() {
        assertThrows(EntityNotFoundException.class, () -> permissionService.deletePermission(-999L));
    }
}
