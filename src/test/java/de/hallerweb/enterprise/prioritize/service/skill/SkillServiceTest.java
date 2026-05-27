package de.hallerweb.enterprise.prioritize.service.skill;

import de.hallerweb.enterprise.prioritize.model.skill.Skill;
import de.hallerweb.enterprise.prioritize.model.skill.SkillCategory;
import de.hallerweb.enterprise.prioritize.repository.skill.SkillCategoryRepository;
import de.hallerweb.enterprise.prioritize.repository.skill.SkillRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;



@SpringBootTest
@ActiveProfiles("postgres")
class SkillServiceTest {

    @Autowired
    private SkillService skillService;

    @Autowired
    private SkillCategoryRepository categoryRepository;

    @Autowired
    private SkillRepository skillRepository;

    SkillCategory mainCat;
    SkillCategory subCat;
    Skill javaSkill;

    @Test
    @DisplayName("Löschen einer Kategorie sollte deren Unterkategorien entfernen und verknüpfte Skills entkoppeln")
    void deleteCategory_ShouldRemoveSubcategoriesAndUnlinkSkills() {
        // 1. ARRANGE: Testdaten in der H2-Testdatenbank aufbauen
        mainCat = new SkillCategory();
        mainCat.setName("IT");
        mainCat = categoryRepository.save(mainCat);

        subCat = new SkillCategory();
        subCat.setName("Java Programming");
        subCat.setParentCategory(mainCat);
        subCat = categoryRepository.save(subCat);

        javaSkill = new Skill();
        javaSkill.setName("Spring Boot Basics");
        javaSkill.setCategory(subCat);
        javaSkill = skillRepository.save(javaSkill);

        Long mainCatId = mainCat.getId();
        Long subCatId = subCat.getId();
        Long skillId = javaSkill.getId();

        // 2. ACT: Die eigentliche Löschlogik triggern
        assertDoesNotThrow(() -> skillService.deleteCategory(mainCatId));

        // 3. ASSERT: Überprüfen, ob alles sauber hinterlassen wurde
        assertFalse(categoryRepository.existsById(mainCatId), "Hauptkategorie wurde nicht gelöscht!");
        assertFalse(categoryRepository.existsById(subCatId), "Unterkategorie wurde nicht gelöscht!");

        Optional<Skill> updatedSkill = skillRepository.findById(skillId);
        assertTrue(updatedSkill.isPresent(), "Der Skill selbst wurde fälschlicherweise gelöscht!");
        assertNull(updatedSkill.get().getCategory(), "Die Verknüpfung des Skills zur Kategorie wurde nicht gekappt!");
    }

    @AfterEach
    void tearDown() {
        // Wird garantiert nach JEDEM Test ausgeführt, egal ob erfolgreich oder fehlgeschlagen
        if (skillRepository.findById(javaSkill.getId()).isPresent()) {
            skillRepository.deleteById(javaSkill.getId());
        }
    }

}