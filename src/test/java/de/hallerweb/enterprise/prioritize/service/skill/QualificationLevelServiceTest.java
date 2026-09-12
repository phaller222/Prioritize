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

package de.hallerweb.enterprise.prioritize.service.skill;

import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.cost.CostRateUnit;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.skill.QualificationLevel;
import de.hallerweb.enterprise.prioritize.repository.company.DepartmentRepository;
import de.hallerweb.enterprise.prioritize.repository.skill.QualificationLevelRepository;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class QualificationLevelServiceTest {

    @Autowired
    private QualificationLevelService qualificationLevelService;

    @Autowired
    private QualificationLevelRepository qualificationLevelRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserService userService;

    private PUser adminUser;
    private Department testDept;

    @BeforeEach
    void setUp() {
        adminUser = userService.findUserByUsername("admin");
        testDept = departmentRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Kein Department gefunden - InitializationService nicht gelaufen?"));
    }

    // ==========================================
    // createQualificationLevel
    // ==========================================

    @Test
    @DisplayName("createQualificationLevel: Stufe wird mit Satz persistiert, Währung normalisiert")
    void createQualificationLevel_ShouldPersistAndNormalizeCurrency() {
        QualificationLevel created = qualificationLevelService.createQualificationLevel(
                level("Geselle", new BigDecimal("58.00"), "eur", CostRateUnit.HOUR), adminUser);

        assertNotNull(created.getId());
        assertEquals("Geselle", created.getName());
        assertEquals(0, new BigDecimal("58.00").compareTo(created.getCostRate()));
        assertEquals("EUR", created.getCostCurrency());
        assertEquals(CostRateUnit.HOUR, created.getCostRateUnit());
    }

    @Test
    @DisplayName("createQualificationLevel: Stufe ohne Satz ist erlaubt")
    void createQualificationLevel_WithoutRate_ShouldBeAllowed() {
        QualificationLevel created = qualificationLevelService.createQualificationLevel(
                level("Ungelernt", null, null, null), adminUser);

        assertNotNull(created.getId());
        assertNull(created.getCostRate());
        assertNull(created.getCostCurrency());
        assertNull(created.getCostRateUnit());
    }

    @Test
    @DisplayName("createQualificationLevel: halber Satz wird abgelehnt")
    void createQualificationLevel_WithPartialRate_ShouldThrow() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                qualificationLevelService.createQualificationLevel(
                        level("Halb", new BigDecimal("10.00"), null, null), adminUser));

        assertTrue(ex.getMessage().contains("all three or none"));
    }

    @Test
    @DisplayName("createQualificationLevel: negativer Satz wird abgelehnt")
    void createQualificationLevel_WithNegativeRate_ShouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
                qualificationLevelService.createQualificationLevel(
                        level("Minus", new BigDecimal("-1.00"), "EUR", CostRateUnit.HOUR), adminUser));
    }

    @Test
    @DisplayName("createQualificationLevel: unbekannte Währung wird abgelehnt")
    void createQualificationLevel_WithUnknownCurrency_ShouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
                qualificationLevelService.createQualificationLevel(
                        level("Taler", new BigDecimal("5.00"), "Euro", CostRateUnit.HOUR), adminUser));
    }

    @Test
    @DisplayName("createQualificationLevel: Name ist Pflicht")
    void createQualificationLevel_WithoutName_ShouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
                qualificationLevelService.createQualificationLevel(
                        level("   ", new BigDecimal("5.00"), "EUR", CostRateUnit.HOUR), adminUser));
    }

    @Test
    @DisplayName("createQualificationLevel: Name ist unabhängig von Groß-/Kleinschreibung eindeutig")
    void createQualificationLevel_WithDuplicateName_ShouldThrow() {
        qualificationLevelService.createQualificationLevel(
                level("Meister", new BigDecimal("72.00"), "EUR", CostRateUnit.HOUR), adminUser);

        assertThrows(IllegalArgumentException.class, () ->
                qualificationLevelService.createQualificationLevel(
                        level("meister", new BigDecimal("70.00"), "EUR", CostRateUnit.HOUR), adminUser));
    }

    // ==========================================
    // updateQualificationLevel
    // ==========================================

    @Test
    @DisplayName("updateQualificationLevel: ausgelassene Kostenfelder löschen den Satz")
    void updateQualificationLevel_WithoutCostFields_ShouldClearRate() {
        QualificationLevel created = qualificationLevelService.createQualificationLevel(
                level("Praktikant", new BigDecimal("12.00"), "EUR", CostRateUnit.HOUR), adminUser);

        QualificationLevel updated = qualificationLevelService.updateQualificationLevel(
                created.getId(), level("Praktikant", null, null, null), adminUser);

        assertNull(updated.getCostRate());
        assertNull(updated.getCostCurrency());
        assertNull(updated.getCostRateUnit());
    }

    @Test
    @DisplayName("updateQualificationLevel: Umbenennen auf einen vergebenen Namen wird abgelehnt")
    void updateQualificationLevel_ToTakenName_ShouldThrow() {
        qualificationLevelService.createQualificationLevel(
                level("Stufe A", null, null, null), adminUser);
        QualificationLevel b = qualificationLevelService.createQualificationLevel(
                level("Stufe B", null, null, null), adminUser);

        assertThrows(IllegalArgumentException.class, () ->
                qualificationLevelService.updateQualificationLevel(
                        b.getId(), level("stufe a", null, null, null), adminUser));
    }

    @Test
    @DisplayName("updateQualificationLevel: unbekannte Id wirft EntityNotFound")
    void updateQualificationLevel_WithUnknownId_ShouldThrow() {
        assertThrows(EntityNotFoundException.class, () ->
                qualificationLevelService.updateQualificationLevel(
                        999999L, level("Egal", null, null, null), adminUser));
    }

    // ==========================================
    // assignQualificationLevel
    // ==========================================

    @Test
    @DisplayName("assignQualificationLevel: Nutzer bekommt die Stufe, null nimmt sie wieder weg")
    void assignQualificationLevel_ShouldSetAndClear() {
        QualificationLevel level = qualificationLevelService.createQualificationLevel(
                level("Geselle-Zuweisung", new BigDecimal("58.00"), "EUR", CostRateUnit.HOUR), adminUser);
        PUser worker = createUser("zuweisung-test");

        PUser assigned = qualificationLevelService.assignQualificationLevel(
                worker.getId(), level.getId(), adminUser);
        assertNotNull(assigned.getQualificationLevel());
        assertEquals(level.getId(), assigned.getQualificationLevel().getId());

        PUser cleared = qualificationLevelService.assignQualificationLevel(worker.getId(), null, adminUser);
        assertNull(cleared.getQualificationLevel());
    }

    // ==========================================
    // deleteQualificationLevel
    // ==========================================

    @Test
    @DisplayName("deleteQualificationLevel: solange jemand zugeordnet ist, wird gelöscht abgelehnt")
    void deleteQualificationLevel_WhileAssigned_ShouldThrow() {
        QualificationLevel level = qualificationLevelService.createQualificationLevel(
                level("Noch-in-Benutzung", null, null, null), adminUser);
        PUser worker = createUser("loesch-test");
        qualificationLevelService.assignQualificationLevel(worker.getId(), level.getId(), adminUser);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                qualificationLevelService.deleteQualificationLevel(level.getId(), adminUser));
        assertTrue(ex.getMessage().contains("still assigned"));

        // ... und nach dem Abmelden geht es
        qualificationLevelService.assignQualificationLevel(worker.getId(), null, adminUser);
        qualificationLevelService.deleteQualificationLevel(level.getId(), adminUser);
        assertTrue(qualificationLevelRepository.findById(level.getId()).isEmpty());
    }

    // ==========================================
    // Berechtigungen
    // ==========================================

    @Test
    @DisplayName("Ohne Berechtigung ist eine Qualifikationsstufe weder lesbar noch anlegbar")
    void withoutPermission_ShouldDenyReadAndCreate() {
        qualificationLevelService.createQualificationLevel(
                level("Sichtbarkeit", new BigDecimal("99.00"), "EUR", CostRateUnit.HOUR), adminUser);
        PUser plainUser = createUser("ohne-rechte");

        assertThrows(AccessDeniedException.class, () ->
                qualificationLevelService.getAllQualificationLevels(plainUser));
        assertThrows(AccessDeniedException.class, () ->
                qualificationLevelService.createQualificationLevel(
                        level("Verboten", null, null, null), plainUser));
    }

    @Test
    @DisplayName("getAllQualificationLevels: liefert die Stufen nach Namen sortiert")
    void getAllQualificationLevels_ShouldBeSortedByName() {
        qualificationLevelService.createQualificationLevel(level("Zzz-Stufe", null, null, null), adminUser);
        qualificationLevelService.createQualificationLevel(level("Aaa-Stufe", null, null, null), adminUser);

        List<String> names = qualificationLevelService.getAllQualificationLevels(adminUser).stream()
                .map(QualificationLevel::getName)
                .toList();

        assertTrue(names.indexOf("Aaa-Stufe") < names.indexOf("Zzz-Stufe"));
    }

    // ==========================================
    // Helfer
    // ==========================================

    private static QualificationLevel level(String name, BigDecimal rate, String currency, CostRateUnit unit) {
        return QualificationLevel.builder()
                .name(name)
                .description("Testdaten")
                .costRate(rate)
                .costCurrency(currency)
                .costRateUnit(unit)
                .build();
    }

    private PUser createUser(String username) {
        PUser user = new PUser();
        user.setUsername(username);
        user.setName("Test");
        user.setFirstname("Nutzer");
        user.setPassword("geheim");
        user.setDepartment(testDept);
        user.setAdmin(false);
        return userService.createUser(user);
    }
}
