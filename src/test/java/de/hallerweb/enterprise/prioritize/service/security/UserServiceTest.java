package de.hallerweb.enterprise.prioritize.service.security;

import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.security.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class UserServiceTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;

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
}