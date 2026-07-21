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

import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.security.Role;
import de.hallerweb.enterprise.prioritize.repository.security.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class UserServiceTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleService roleService;

    private PUser testUser;

    @BeforeEach
    void setUp() {
        testUser = PUser.builder()
                .username("test-user-" + System.currentTimeMillis()) // eindeutig
                .name("Testmann")
                .firstname("Max")
                .email("max.test@example.com")
                .password("plaintext123") // is encrypted in createUser
                .admin(false)
                .build();
        testUser = userService.createUser(testUser);
    }

    // ==========================================
    // createUser
    // ==========================================

    @Test
    @DisplayName("createUser: Passwort wird verschlüsselt gespeichert")
    void createUser_PasswordShouldBeEncoded() {
        assertNotEquals("plaintext123", testUser.getPassword());
        assertTrue(testUser.getPassword().startsWith("$2a$")); // BCrypt-Prefix
    }

    /** A second user carrying the given name, ready to be handed to createUser. */
    private PUser userNamed(String username) {
        return PUser.builder()
                .username(username)
                .name("Zweiter")
                .firstname("Max")
                .email("max2.test@example.com")
                .password("plaintext123")
                .admin(false)
                .build();
    }

    @Test
    @DisplayName("createUser: ein vergebener Username wird abgelehnt")
    void createUser_duplicateUsername_throws() {
        PUser duplicate = userNamed(testUser.getUsername());
        assertThrows(IllegalStateException.class, () -> userService.createUser(duplicate));
    }

    @Test
    @DisplayName("createUser: die Prüfung ist case-insensitiv (kein 'Admin' neben 'admin')")
    void createUser_duplicateUsernameIgnoringCase_throws() {
        PUser duplicate = userNamed(testUser.getUsername().toUpperCase());
        assertThrows(IllegalStateException.class, () -> userService.createUser(duplicate));
    }

    @Test
    @DisplayName("createUser: ein deaktivierter Account belegt seinen Namen weiterhin")
    void createUser_nameOfDeactivatedUserStaysTaken() {
        userService.deactivateUser(testUser.getId());
        PUser duplicate = userNamed(testUser.getUsername());

        assertThrows(IllegalStateException.class, () -> userService.createUser(duplicate));
    }

    @Test
    @DisplayName("updateUser: darf den eigenen Namen behalten, aber keinen fremden übernehmen")
    void updateUser_usernameCollision() {
        PUser other = userService.createUser(userNamed("test-other-" + System.nanoTime()));

        // Keeping one's own name is not a collision.
        testUser.setName("Umbenannt");
        assertDoesNotThrow(() -> userService.updateUser(testUser));

        other.setUsername(testUser.getUsername());
        assertThrows(IllegalStateException.class, () -> userService.updateUser(other));
    }

    @Test
    @DisplayName("createUser: User wird mit ID persistiert")
    void createUser_ShouldPersistWithId() {
        assertNotNull(testUser.getId());
        assertTrue(userRepository.existsById(testUser.getId()));
    }

    // ==========================================
    // getUserById
    // ==========================================

    @Test
    @DisplayName("getUserById: Existierender User wird korrekt zurückgegeben")
    void getUserById_ShouldReturnUser() {
        PUser found = userService.getUserById(testUser.getId());
        assertEquals(testUser.getUsername(), found.getUsername());
        assertEquals("Testmann", found.getName());
    }

    @Test
    @DisplayName("getUserById: Unbekannte ID wirft NoSuchElementException")
    void getUserById_UnknownId_ShouldThrow() {
        assertThrows(NoSuchElementException.class,
                () -> userService.getUserById(-999L));
    }

    // ==========================================
    // findUserByUsername
    // ==========================================

    @Test
    @DisplayName("findUserByUsername: Bekannter Username liefert korrekten User")
    void findUserByUsername_ShouldReturnUser() {
        PUser found = userService.findUserByUsername(testUser.getUsername());
        assertEquals(testUser.getId(), found.getId());
    }

    @Test
    @DisplayName("findUserByUsername: Unbekannter Username wirft NoSuchElementException")
    void findUserByUsername_Unknown_ShouldThrow() {
        assertThrows(NoSuchElementException.class,
                () -> userService.findUserByUsername("ghost-user-xyz"));
    }

    // ==========================================
    // partialUpdateUser
    // ==========================================

    @Test
    @DisplayName("partialUpdateUser: E-Mail wird aktualisiert, Passwort bleibt unverändert")
    void partialUpdate_ShouldUpdateEmailOnly() {
        String oldPassword = testUser.getPassword();

        PUser patch = new PUser();
        patch.setEmail("new.email@example.com");

        PUser updated = userService.partialUpdateUser(testUser.getId(), patch);

        assertEquals("new.email@example.com", updated.getEmail());
        assertEquals("Testmann", updated.getName()); // unchanged
        assertEquals(oldPassword, updated.getPassword()); // unchanged
    }

    @Test
    @DisplayName("partialUpdateUser: Passwort wird neu verschlüsselt wenn mitgeschickt")
    void partialUpdate_ShouldReencodePassword() {
        String oldPassword = testUser.getPassword();

        PUser patch = new PUser();
        patch.setPassword("neuesPasswort456");

        PUser updated = userService.partialUpdateUser(testUser.getId(), patch);

        assertNotEquals(oldPassword, updated.getPassword());
        assertTrue(updated.getPassword().startsWith("$2a$"));
    }

    @Test
    @DisplayName("partialUpdateUser: admin-Flag kann nicht per PATCH geändert werden")
    void partialUpdate_ShouldNotChangeAdminFlag() {
        PUser patch = new PUser();
        patch.setAdmin(true); // Attempt to make oneself an admin

        PUser updated = userService.partialUpdateUser(testUser.getId(), patch);

        assertFalse(updated.isAdmin()); // must remain false
    }

    // ==========================================
    // deactivateUser
    // ==========================================

    @Test
    @DisplayName("deactivateUser: User wird deaktiviert")
    void deactivateUser_ShouldSetActiveFalse() {
        userService.deactivateUser(testUser.getId());
        PUser raw = userRepository.findById(testUser.getId()).orElseThrow();
        assertFalse(raw.isActive());
    }

    @Test
    @DisplayName("deactivateUser: Admin-User kann nicht deaktiviert werden")
    void deactivateUser_Admin_ShouldThrow() {
        PUser adminUser = userService.findUserByUsername("admin");
        assertThrows(IllegalArgumentException.class,
                () -> userService.deactivateUser(adminUser.getId()));
    }

    @Test
    @DisplayName("deactivateUser: Unbekannte ID wirft NoSuchElementException")
    void deactivateUser_UnknownId_ShouldThrow() {
        assertThrows(NoSuchElementException.class,
                () -> userService.deactivateUser(-999L));
    }

    // ==========================================
    // active-Flag: Absicherung aller Ladepfade
    // ==========================================

    @Test
    @DisplayName("getUserById: Deaktivierter User wirft NoSuchElementException")
    void getUserById_InactiveUser_ShouldThrow() {
        userService.deactivateUser(testUser.getId());
        assertThrows(NoSuchElementException.class,
                () -> userService.getUserById(testUser.getId()));
    }

    @Test
    @DisplayName("findUserByUsername: Deaktivierter User wirft NoSuchElementException")
    void findUserByUsername_InactiveUser_ShouldThrow() {
        userService.deactivateUser(testUser.getId());
        assertThrows(NoSuchElementException.class,
                () -> userService.findUserByUsername(testUser.getUsername()));
    }

    @Test
    @DisplayName("getAllUsers: Deaktivierter User erscheint nicht in der Liste")
    void getAllUsers_ShouldNotContainInactiveUser() {
        userService.deactivateUser(testUser.getId());
        assertFalse(userService.getAllUsers().stream()
                .anyMatch(u -> u.getId().equals(testUser.getId())));
    }

    // ==========================================
    // setRoles / getRoleIds (role assignment)
    // ==========================================

    private Role newRole(String name) {
        return roleService.createRole(
                Role.builder().name(name + "-" + System.nanoTime()).description("test role").build(), null);
    }

    @Test
    @DisplayName("getRoleIds: Neuer User hat keine Rollen")
    void getRoleIds_NewUser_ShouldBeEmpty() {
        assertTrue(userService.getRoleIds(testUser.getId()).isEmpty());
    }

    @Test
    @DisplayName("setRoles: Weist Rollen zu und getRoleIds spiegelt sie wider")
    void setRoles_ShouldAssignRoles() {
        Role r1 = newRole("Manager");
        Role r2 = newRole("Reviewer");

        userService.setRoles(testUser.getId(), Set.of(r1.getId(), r2.getId()));

        assertEquals(Set.of(r1.getId(), r2.getId()), userService.getRoleIds(testUser.getId()));
    }

    @Test
    @DisplayName("setRoles: Vollständiges Ersetzen — alte Rollen ohne Wirkung entfernt")
    void setRoles_ShouldReplaceExistingRoles() {
        Role r1 = newRole("Manager");
        Role r2 = newRole("Reviewer");
        userService.setRoles(testUser.getId(), Set.of(r1.getId()));

        userService.setRoles(testUser.getId(), Set.of(r2.getId()));

        assertEquals(Set.of(r2.getId()), userService.getRoleIds(testUser.getId()));
    }

    @Test
    @DisplayName("setRoles: Leere Menge entfernt alle Rollen")
    void setRoles_EmptySet_ShouldClearRoles() {
        Role r1 = newRole("Manager");
        userService.setRoles(testUser.getId(), Set.of(r1.getId()));

        userService.setRoles(testUser.getId(), Set.of());

        assertTrue(userService.getRoleIds(testUser.getId()).isEmpty());
    }

    @Test
    @DisplayName("setRoles: null entfernt alle Rollen")
    void setRoles_Null_ShouldClearRoles() {
        Role r1 = newRole("Manager");
        userService.setRoles(testUser.getId(), Set.of(r1.getId()));

        userService.setRoles(testUser.getId(), null);

        assertTrue(userService.getRoleIds(testUser.getId()).isEmpty());
    }

    @Test
    @DisplayName("setRoles: Unbekannte Rollen-ID wirft EntityNotFoundException")
    void setRoles_UnknownRoleId_ShouldThrow() {
        assertThrows(EntityNotFoundException.class,
                () -> userService.setRoles(testUser.getId(), Set.of(999999L)));
    }
}