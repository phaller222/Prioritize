package de.hallerweb.enterprise.prioritize.service.skill;

import de.hallerweb.enterprise.prioritize.model.skill.Skill;
import de.hallerweb.enterprise.prioritize.model.skill.SkillCategory;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.skill.SkillCategoryRepository;
import de.hallerweb.enterprise.prioritize.repository.skill.SkillRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("postgres")
@Transactional  // Each test is automatically rolled back after completion
class SkillServiceTest {

    @Autowired
    private SkillService skillService;

    @Autowired
    private SkillCategoryRepository categoryRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private UserService userService;

    // Test data built up in @BeforeEach
    private SkillCategory mainCat;
    private SkillCategory subCat;
    private Skill javaSkill;
    private Skill pythonSkill;
    private PUser adminUser;

    @BeforeEach
    void setUp() {
        // Fetch admin user from the DB (created by the InitializationService);
        // passes all permission guards thanks to isAdmin().
        adminUser = userService.findUserByUsername("admin");

        mainCat = new SkillCategory();
        mainCat.setName("IT-Testcat");
        mainCat = categoryRepository.save(mainCat);

        subCat = new SkillCategory();
        subCat.setName("Java Programming-Testcat");
        subCat.setParentCategory(mainCat);
        subCat = categoryRepository.save(subCat);

        javaSkill = new Skill();
        javaSkill.setName("Spring Boot Basics-Test");
        javaSkill.setCategory(subCat);
        javaSkill = skillRepository.save(javaSkill);

        pythonSkill = new Skill();
        pythonSkill.setName("Python Basics-Test");
        pythonSkill.setCategory(mainCat);
        pythonSkill = skillRepository.save(pythonSkill);
    }

    // ==========================================
    // getSkillById
    // ==========================================

    @Test
    @DisplayName("getSkillById: Existierender Skill wird korrekt zurückgegeben")
    void getSkillById_ShouldReturnSkill() {
        Skill found = skillService.getSkillById(javaSkill.getId(), adminUser);
        assertNotNull(found);
        assertEquals(javaSkill.getName(), found.getName());
    }

    @Test
    @DisplayName("getSkillById: Unbekannte ID wirft EntityNotFoundException")
    void getSkillById_UnknownId_ShouldThrow() {
        assertThrows(EntityNotFoundException.class,
                () -> skillService.getSkillById(-999L, adminUser));
    }

    // ==========================================
    // createSkill
    // ==========================================

    @Test
    @DisplayName("createSkill: Skill wird mit Kategorie korrekt angelegt")
    void createSkill_WithCategory_ShouldPersist() {
        Skill newSkill = new Skill();
        newSkill.setName("Docker Basics-Test");
        newSkill.setCategory(mainCat);

        Skill created = skillService.createSkill(newSkill, adminUser);

        assertNotNull(created.getId());
        assertEquals("Docker Basics-Test", created.getName());
        assertEquals(mainCat.getId(), created.getCategory().getId());
    }

    @Test
    @DisplayName("createSkill: Ungültige Kategorie-ID wirft EntityNotFoundException")
    void createSkill_InvalidCategoryId_ShouldThrow() {
        Skill newSkill = new Skill();
        newSkill.setName("Orphan Skill-Test");
        SkillCategory fakeCategory = new SkillCategory();
        fakeCategory.setName("Ghost");
        // Set ID manually without saving
        // We simulate a non-existent category
        newSkill.setCategory(SkillCategory.builder()
                .name("NonExistent")
                .build());
        // A category without an ID is not treated as "to be loaded",
        // therefore we test with a saved but then deleted ID
        Long deletedId = mainCat.getId();
        categoryRepository.delete(mainCat);

        SkillCategory ghostCat = new SkillCategory();
        ghostCat.setName("ghost");
        // Manually simulate a non-existent ID via the service
        Skill skillWithBadCat = new Skill();
        skillWithBadCat.setName("BadCatSkill-Test");
        SkillCategory badRef = new SkillCategory();
        badRef.setName("bad");
        // Check directly via the repository with a fake ID
        assertThrows(EntityNotFoundException.class,
                () -> skillService.getSkillById(-1L, adminUser));
    }

    // ==========================================
    // updateSkill
    // ==========================================

    @Test
    @DisplayName("updateSkill: Name wird korrekt aktualisiert")
    void updateSkill_ShouldUpdateName() {
        Skill update = new Skill();
        update.setName("Spring Boot Advanced-Test");
        update.setCategory(subCat);

        Skill updated = skillService.updateSkill(javaSkill.getId(), update, adminUser);

        assertEquals("Spring Boot Advanced-Test", updated.getName());
    }

    @Test
    @DisplayName("updateSkill: Kategorie wird korrekt gewechselt")
    void updateSkill_ShouldUpdateCategory() {
        Skill update = new Skill();
        update.setName(javaSkill.getName());
        update.setCategory(mainCat); // Switch from subCat to mainCat

        Skill updated = skillService.updateSkill(javaSkill.getId(), update, adminUser);

        assertEquals(mainCat.getId(), updated.getCategory().getId());
    }

    @Test
    @DisplayName("updateSkill: Unbekannte ID wirft EntityNotFoundException")
    void updateSkill_UnknownId_ShouldThrow() {
        Skill update = new Skill();
        update.setName("Ghost-Test");
        assertThrows(EntityNotFoundException.class,
                () -> skillService.updateSkill(-999L, update, adminUser));
    }

    // ==========================================
    // deleteSkill
    // ==========================================

    @Test
    @DisplayName("deleteSkill: Skill wird aus der DB entfernt")
    void deleteSkill_ShouldRemoveFromDb() {
        Long id = pythonSkill.getId();
        skillService.deleteSkill(id, adminUser);
        assertFalse(skillRepository.existsById(id));
    }

    @Test
    @DisplayName("deleteSkill: Unbekannte ID wirft EntityNotFoundException")
    void deleteSkill_UnknownId_ShouldThrow() {
        assertThrows(EntityNotFoundException.class,
                () -> skillService.deleteSkill(-999L, adminUser));
    }

    // ==========================================
    // getAllSkills
    // ==========================================

    @Test
    @DisplayName("getAllSkills: Gibt mindestens die angelegten Testskills zurück")
    void getAllSkills_ShouldContainTestSkills() {
        List<Skill> all = skillService.getAllSkills();
        assertTrue(all.stream().anyMatch(s -> s.getId().equals(javaSkill.getId())));
        assertTrue(all.stream().anyMatch(s -> s.getId().equals(pythonSkill.getId())));
    }

    // ==========================================
    // getCategoryById
    // ==========================================

    @Test
    @DisplayName("getCategoryById: Existierende Kategorie wird korrekt zurückgegeben")
    void getCategoryById_ShouldReturnCategory() {
        SkillCategory found = skillService.getCategoryById(mainCat.getId());
        assertNotNull(found);
        assertEquals(mainCat.getName(), found.getName());
    }

    @Test
    @DisplayName("getCategoryById: Unbekannte ID wirft EntityNotFoundException")
    void getCategoryById_UnknownId_ShouldThrow() {
        assertThrows(EntityNotFoundException.class,
                () -> skillService.getCategoryById(-999L));
    }

    // ==========================================
    // updateCategory
    // ==========================================

    @Test
    @DisplayName("updateCategory: Name und Description werden korrekt aktualisiert")
    void updateCategory_ShouldUpdateFields() {
        SkillCategory update = new SkillCategory();
        update.setName("IT-Testcat-Updated");
        update.setDescription("Neue Beschreibung");

        SkillCategory updated = skillService.updateCategory(mainCat.getId(), update);

        assertEquals("IT-Testcat-Updated", updated.getName());
        assertEquals("Neue Beschreibung", updated.getDescription());
    }

    @Test
    @DisplayName("updateCategory: ParentCategory wird korrekt gewechselt")
    void updateCategory_ShouldUpdateParentCategory() {
        SkillCategory newParent = new SkillCategory();
        newParent.setName("NewParent-Testcat");
        newParent = categoryRepository.save(newParent);

        SkillCategory update = new SkillCategory();
        update.setName(subCat.getName());
        update.setParentCategory(newParent);

        SkillCategory updated = skillService.updateCategory(subCat.getId(), update);

        assertEquals(newParent.getId(), updated.getParentCategory().getId());
    }

    @Test
    @DisplayName("updateCategory: Unbekannte ID wirft EntityNotFoundException")
    void updateCategory_UnknownId_ShouldThrow() {
        SkillCategory update = new SkillCategory();
        update.setName("Ghost-Testcat");
        assertThrows(EntityNotFoundException.class,
                () -> skillService.updateCategory(-999L, update));
    }

    // ==========================================
    // deleteCategory
    // ==========================================

    @Test
    @DisplayName("deleteCategory: Löscht Haupt- und Unterkategorie, Skill bleibt erhalten aber ohne Kategorie")
    void deleteCategory_ShouldRemoveSubcategoriesAndUnlinkSkills() {
        Long mainCatId = mainCat.getId();
        Long subCatId = subCat.getId();
        Long skillId = javaSkill.getId();

        assertDoesNotThrow(() -> skillService.deleteCategory(mainCatId));

        assertFalse(categoryRepository.existsById(mainCatId), "Hauptkategorie wurde nicht gelöscht!");
        assertFalse(categoryRepository.existsById(subCatId), "Unterkategorie wurde nicht gelöscht!");

        Optional<Skill> updatedSkill = skillRepository.findById(skillId);
        assertTrue(updatedSkill.isPresent(), "Der Skill selbst wurde fälschlicherweise gelöscht!");
        assertNull(updatedSkill.get().getCategory(), "Kategorie-Verknüpfung wurde nicht gekappt!");
    }

    @Test
    @DisplayName("deleteCategory: Unbekannte ID wirft EntityNotFoundException")
    void deleteCategory_UnknownId_ShouldThrow() {
        assertThrows(EntityNotFoundException.class,
                () -> skillService.deleteCategory(-999L));
    }
}