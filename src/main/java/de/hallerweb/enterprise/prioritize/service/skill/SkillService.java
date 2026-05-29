package de.hallerweb.enterprise.prioritize.service.skill;

import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.skill.Skill;
import de.hallerweb.enterprise.prioritize.model.skill.SkillCategory;
import de.hallerweb.enterprise.prioritize.model.skill.SkillRecord;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceRepository;
import de.hallerweb.enterprise.prioritize.repository.skill.SkillCategoryRepository;
import de.hallerweb.enterprise.prioritize.repository.skill.SkillRecordRepository;
import de.hallerweb.enterprise.prioritize.repository.skill.SkillRepository;
import de.hallerweb.enterprise.prioritize.service.security.UserService; // Import deines UserServices
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository skillRepository;
    private final SkillCategoryRepository skillCategoryRepository;
    private final SkillRecordRepository skillRecordRepository;
    private final ResourceRepository resourceRepository;
    private final UserService userService;


    private final EntityManager entityManager;

    // --- GLOBALE SKILLS & KATEGORIEN ---

    @Transactional(readOnly = true)
    public List<Skill> getAllSkills() {
        return skillRepository.findAll();
    }

    @Transactional
    public Skill createSkill(Skill skill) {
        if (skill.getCategory() != null && skill.getCategory().getId() != null) {
            SkillCategory category = skillCategoryRepository.findById(skill.getCategory().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Kategorie mit ID " + skill.getCategory().getId() + " nicht gefunden"));
            skill.setCategory(category);
        }

        if (skill.getSkillProperties() != null) {
            skill.getSkillProperties().forEach(prop -> prop.setSkill(skill));
        }

        return skillRepository.save(skill);
    }

    @Transactional
    public SkillCategory createCategory(SkillCategory category) {
        return skillCategoryRepository.save(category);
    }

    // --- SKILL RECORDS (USER ZUORDNUNG) ---

    @Transactional(readOnly = true)
    public Set<SkillRecord> getSkillsForUser(Long userId) {
        return skillRecordRepository.findByUserId(userId);
    }

    @Transactional
    public SkillRecord assignSkillToUser(Long userId, SkillRecord record) {
        // 1. Den echten User über deinen UserService laden
        PUser user = userService.getUserById(userId);
        record.setUser(user);

        // 2. Den globalen Skill verifizieren
        Skill skill = skillRepository.findById(record.getSkill().getId())
                .orElseThrow(() -> new EntityNotFoundException("Skill nicht gefunden"));
        record.setSkill(skill);

        // Wir prüfen, ob der User bereits einen Record für DIESEN spezifischen Skill hat
        if (user.getSkills() != null) {
            boolean skillAlreadyAssigned = user.getSkills().stream()
                    .anyMatch(existingRecord -> existingRecord.getSkill().getId().equals(skill.getId()));

            if (skillAlreadyAssigned) {
                throw new IllegalArgumentException("Dieser Skill wurde dem Benutzer bereits zugewiesen!");
                // Alternativ kannst du eine eigene Exception werfen, z.B. SkillAlreadyAssignedException
            }
        }


        if (user.getSkills() != null) {
            user.getSkills().add(record);
        }

        return skillRecordRepository.save(record);
    }

    // --- SKILL RECORDS (RESSOURCEN ZUORDNUNG) ---

    @Transactional(readOnly = true)
    public Set<SkillRecord> getSkillsForResource(Long resourceId) {
        return skillRecordRepository.findByResourceId(resourceId);
    }

    @Transactional
    public SkillRecord assignSkillToResource(Long resourceId, SkillRecord record) {
        // 1. Ressource laden
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new EntityNotFoundException("Resource mit ID " + resourceId + " nicht gefunden"));
        record.setResource(resource);

        // 2. Skill verifizieren
        Skill skill = skillRepository.findById(record.getSkill().getId())
                .orElseThrow(() -> new EntityNotFoundException("Skill nicht gefunden"));
        record.setSkill(skill);

        return skillRecordRepository.save(record);
    }

    @Transactional(readOnly = true)
    public List<SkillCategory> getAllCategories() {
        return skillCategoryRepository.findAllWithSubCategories();
    }

    @Transactional(readOnly = true)
    public SkillCategory getCategoryById(Long categoryId) {
        return skillCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Kategorie mit ID " + categoryId + " nicht gefunden"));
    }

    @Transactional
    public SkillCategory updateCategory(Long categoryId, SkillCategory categoryDetails) {
        SkillCategory existing = getCategoryById(categoryId);
        existing.setName(categoryDetails.getName());
        existing.setDescription(categoryDetails.getDescription());

        if (categoryDetails.getParentCategory() != null && categoryDetails.getParentCategory().getId() != null) {
            SkillCategory parent = skillCategoryRepository.findById(categoryDetails.getParentCategory().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Parent-Kategorie mit ID " + categoryDetails.getParentCategory().getId() + " nicht gefunden"));
            existing.setParentCategory(parent);
        } else {
            existing.setParentCategory(null);
        }

        return skillCategoryRepository.save(existing);
    }

    @Transactional
    public void deleteSkill(Long skillId) {
        if (!skillRepository.existsById(skillId)) {
            throw new EntityNotFoundException("Skill mit ID " + skillId + " nicht gefunden");
        }
        skillRepository.deleteById(skillId);
    }

    @Transactional
    public void deleteCategory(Long categoryId) {
        // 1. Prüfen, ob die zu löschende Kategorie überhaupt existiert
        if (!skillCategoryRepository.existsById(categoryId)) {
            throw new EntityNotFoundException("Kategorie mit ID " + categoryId + " nicht gefunden");
        }

        // 2. SCHRITT 1: Finde alle IDs im Unterbaum direkt auf DB-Ebene mit einer rekursiven Abfrage (PostgreSQL CTE)
        String cteQuery =
                "WITH RECURSIVE subcategories(id) AS (" +  // <-- Hier das (id) für H2-Kompatibilität hinzugefügt!
                        "    SELECT id FROM skill_category WHERE id = :rootId" +
                        "    UNION ALL" +
                        "    SELECT c.id FROM skill_category c" +
                        "    INNER JOIN subcategories s ON c.parent_category_id = s.id" +
                        ") SELECT id FROM subcategories";

        @SuppressWarnings("unchecked")
        List<Long> allCategoryIdsToDelete = entityManager.createNativeQuery(cteQuery)
                .setParameter("rootId", categoryId)
                .getResultList();

        if (!allCategoryIdsToDelete.isEmpty()) {

            // 3. SCHRITT 2: Alle betroffenen Skills von diesen Kategorien lösen (Fremdschlüssel nullen)
            entityManager.createNativeQuery("UPDATE skill SET category_id = NULL WHERE category_id IN :ids")
                    .setParameter("ids", allCategoryIdsToDelete)
                    .executeUpdate();

            // 4. SCHRITT 3: Die Eltern-Kind-Beziehungen innerhalb des Baums auflösen
            entityManager.createNativeQuery("UPDATE skill_category SET parent_category_id = NULL WHERE id IN :ids")
                    .setParameter("ids", allCategoryIdsToDelete)
                    .executeUpdate();

            // 5. SCHRITT 4: Jetzt den gesamten Ast rückstandslos entfernen
            entityManager.createNativeQuery("DELETE FROM skill_category WHERE id IN :ids")
                    .setParameter("ids", allCategoryIdsToDelete)
                    .executeUpdate();
        }

        // 6. Hibernate-Cache komplett leeren, damit die gelöschten Entitäten im RAM nicht mehr existieren
        entityManager.clear();
    }

    /**
     * Öffnet eine isolierte Transaktion, setzt alle Fremdschlüssel auf NULL,
     * speichert dies persistent in der DB ab und schließt die Transaktion sofort wieder.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void breakCategoryRelationships(Set<Integer> ids) {
        // Skills entkoppeln
        entityManager.createNativeQuery("UPDATE skill SET category_id = NULL WHERE category_id IN :ids")
                .setParameter("ids", ids)
                .executeUpdate();

        // Kategorien-Hierarchie flachschlagen (Self-Reference auf NULL setzen)
        entityManager.createNativeQuery("UPDATE skill_category SET parent_category_id = NULL WHERE id IN :ids")
                .setParameter("ids", ids)
                .executeUpdate();

        entityManager.flush();
    }


    private void collectCategoryIdsInMemoryRecursive(SkillCategory category, Set<Long> ids) {
        if (category == null) {
            return;
        }
        ids.add(category.getId());
        if (category.getSubCategories() != null) {
            // Da die Collection durch findAllWithSubCategories() bereits voll im RAM ist,
            // triggert dieser Loop kein veränderndes DB-Nachladen mehr!
            for (SkillCategory sub : category.getSubCategories()) {
                collectCategoryIdsInMemoryRecursive(sub, ids);
            }
        }
    }

    @Transactional(readOnly = true)
    public Skill getSkillById(Long skillId) {
        return skillRepository.findById(skillId)
                .orElseThrow(() -> new EntityNotFoundException("Skill mit ID " + skillId + " nicht gefunden"));
    }

    @Transactional
    public Skill updateSkill(Long skillId, Skill skillDetails) {
        Skill existing = getSkillById(skillId);
        existing.setName(skillDetails.getName());

        if (skillDetails.getCategory() != null && skillDetails.getCategory().getId() != null) {
            SkillCategory category = skillCategoryRepository.findById(skillDetails.getCategory().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Kategorie mit ID " + skillDetails.getCategory().getId() + " nicht gefunden"));
            existing.setCategory(category);
        }

        if (skillDetails.getSkillProperties() != null) {
            existing.getSkillProperties().clear();
            skillDetails.getSkillProperties().forEach(prop -> prop.setSkill(existing));
            existing.getSkillProperties().addAll(skillDetails.getSkillProperties());
        }

        return skillRepository.save(existing);
    }

}