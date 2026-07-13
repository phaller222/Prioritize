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
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.security.PermissionRecord;
import de.hallerweb.enterprise.prioritize.model.security.Role;
import de.hallerweb.enterprise.prioritize.repository.company.CompanyRepository;
import de.hallerweb.enterprise.prioritize.repository.company.DepartmentRepository;
import de.hallerweb.enterprise.prioritize.repository.security.RoleRepository;
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
class RoleServiceTest {

    @Autowired
    private RoleService roleService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserService userService;

    private Department testDept;
    private Role testRole;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder().name("Role-Test GmbH").build());
        testDept = departmentRepository.save(Department.builder()
                .name("Role-Test-Dept")
                .description("Abteilung für Rollen-Tests")
                .company(company)
                .build());

        Role role = Role.builder()
                .name("TESTER-Role")
                .description("Rolle für Tests")
                .build();
        testRole = roleService.createRole(role, testDept.getId());
    }

    // ==========================================
    // getRoleById / getRoleByName
    // ==========================================

    @Test
    @DisplayName("getRoleById: Existierende Rolle wird zurückgegeben")
    void getRoleById_ShouldReturnRole() {
        Role found = roleService.getRoleById(testRole.getId());
        assertNotNull(found);
        assertEquals("TESTER-Role", found.getName());
    }

    @Test
    @DisplayName("getRoleById: Unbekannte ID wirft EntityNotFoundException")
    void getRoleById_UnknownId_ShouldThrow() {
        assertThrows(EntityNotFoundException.class, () -> roleService.getRoleById(-999L));
    }

    @Test
    @DisplayName("getRoleByName: Findet Rolle anhand des Namens")
    void getRoleByName_ShouldReturnRole() {
        Role found = roleService.getRoleByName("TESTER-Role");
        assertEquals(testRole.getId(), found.getId());
    }

    @Test
    @DisplayName("getRoleByName: Unbekannter Name wirft EntityNotFoundException")
    void getRoleByName_UnknownName_ShouldThrow() {
        assertThrows(EntityNotFoundException.class, () -> roleService.getRoleByName("GibtsNicht-Role"));
    }

    // ==========================================
    // searchRoles / getRolesByDepartment
    // ==========================================

    @Test
    @DisplayName("searchRoles: Findet Rollen anhand eines Teilstrings (case-insensitive)")
    void searchRoles_ShouldReturnMatches() {
        List<Role> result = roleService.searchRoles("tester");
        assertTrue(result.stream().anyMatch(r -> r.getId().equals(testRole.getId())));
    }

    @Test
    @DisplayName("getRolesByDepartment: Gibt die Rollen der Abteilung zurück")
    void getRolesByDepartment_ShouldReturnRoles() {
        List<Role> result = roleService.getRolesByDepartment(testDept.getId());
        assertTrue(result.stream().anyMatch(r -> r.getId().equals(testRole.getId())));
    }

    // ==========================================
    // createRole
    // ==========================================

    @Test
    @DisplayName("createRole: Rolle wird persistiert, der Abteilung zugeordnet und mit leerem Permission-Set initialisiert")
    void createRole_ShouldPersistAndScopeAndInitPermissions() {
        Role created = roleService.createRole(
                Role.builder().name("ANOTHER-Role").description("Noch eine").build(), testDept.getId());

        assertNotNull(created.getId());
        assertEquals(testDept.getId(), created.getDepartment().getId());
        assertNotNull(created.getPermissions(), "Permission-Set darf nach createRole nicht null sein");
        assertTrue(created.getPermissions().isEmpty());
        assertTrue(roleRepository.existsById(created.getId()));
    }

    @Test
    @DisplayName("createRole: departmentId null erzeugt eine ungescopte Rolle")
    void createRole_NullDepartment_ShouldBeUnscoped() {
        Role created = roleService.createRole(
                Role.builder().name("GLOBAL-Role").build(), null);
        assertNull(created.getDepartment());
    }

    @Test
    @DisplayName("createRole: Unbekannte Department-ID wirft EntityNotFoundException")
    void createRole_UnknownDepartment_ShouldThrow() {
        assertThrows(EntityNotFoundException.class,
                () -> roleService.createRole(Role.builder().name("Ghost-Role").build(), -999L));
    }

    // ==========================================
    // updateRole
    // ==========================================

    @Test
    @DisplayName("updateRole: Name, Description und Department werden aktualisiert")
    void updateRole_ShouldUpdateFields() {
        Role details = Role.builder().name("TESTER-Role (neu)").description("Aktualisiert").build();
        Role updated = roleService.updateRole(testRole.getId(), details, null);

        assertEquals("TESTER-Role (neu)", updated.getName());
        assertEquals("Aktualisiert", updated.getDescription());
        assertNull(updated.getDepartment(), "Department sollte auf null umgesetzt worden sein");
    }

    @Test
    @DisplayName("updateRole: Unbekannte ID wirft EntityNotFoundException")
    void updateRole_UnknownId_ShouldThrow() {
        Role details = Role.builder().name("Ghost-Role").build();
        assertThrows(EntityNotFoundException.class,
                () -> roleService.updateRole(-999L, details, null));
    }

    // ==========================================
    // deleteRole
    // ==========================================

    @Test
    @DisplayName("deleteRole: Rolle wird entfernt")
    void deleteRole_ShouldRemove() {
        Long id = testRole.getId();
        roleService.deleteRole(id);
        assertThrows(EntityNotFoundException.class, () -> roleService.getRoleById(id));
    }

    @Test
    @DisplayName("deleteRole: Rolle wird vor dem Löschen von ihren Nutzern gelöst")
    void deleteRole_ShouldDetachFromUsers() {
        // Assign the role to a fresh user
        PUser user = userService.createUser(PUser.builder()
                .username("role-test-user")
                .name("Role Tester")
                .password("secret123")
                .active(true)
                .build());
        user.addRole(roleService.getRoleById(testRole.getId()));
        userService.updateUser(user);

        // Deleting the role must not fail on the join-table FK and must clear the assignment
        roleService.deleteRole(testRole.getId());

        PUser reloaded = userService.findUserByUsername("role-test-user");
        assertTrue(reloaded.getRoles().stream().noneMatch(r -> r.getId().equals(testRole.getId())));
    }

    @Test
    @DisplayName("deleteRole: Unbekannte ID wirft EntityNotFoundException")
    void deleteRole_UnknownId_ShouldThrow() {
        assertThrows(EntityNotFoundException.class, () -> roleService.deleteRole(-999L));
    }

    // ==========================================
    // addPermissionToRole / removePermissionFromRole
    // ==========================================

    @Test
    @DisplayName("addPermissionToRole: Permission wird der Rolle hinzugefügt und mitpersistiert")
    void addPermissionToRole_ShouldAddAndPersist() {
        PermissionRecord perm = PermissionRecord.builder()
                .absoluteObjectType("de.hallerweb.enterprise.prioritize.model.company.Company")
                .objectId(0L)
                .readPermission(true)
                .build();

        Role updated = roleService.addPermissionToRole(testRole.getId(), perm);

        assertEquals(1, updated.getPermissions().size());
        PermissionRecord stored = updated.getPermissions().iterator().next();
        assertNotNull(stored.getId(), "Permission sollte durch Cascade eine ID erhalten haben");
        assertTrue(stored.isReadPermission());
    }

    @Test
    @DisplayName("removePermissionFromRole: Permission wird entfernt (orphan removal)")
    void removePermissionFromRole_ShouldRemove() {
        PermissionRecord perm = PermissionRecord.builder()
                .absoluteObjectType("de.hallerweb.enterprise.prioritize.model.company.Company")
                .objectId(0L)
                .readPermission(true)
                .build();
        Role withPerm = roleService.addPermissionToRole(testRole.getId(), perm);
        Long permId = withPerm.getPermissions().iterator().next().getId();

        Role afterRemoval = roleService.removePermissionFromRole(testRole.getId(), permId);

        assertTrue(afterRemoval.getPermissions().isEmpty());
    }
}
