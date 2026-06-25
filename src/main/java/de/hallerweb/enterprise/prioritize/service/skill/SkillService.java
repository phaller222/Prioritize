package de.hallerweb.enterprise.prioritize.service.skill;

import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.skill.Skill;
import de.hallerweb.enterprise.prioritize.model.skill.SkillCategory;
import de.hallerweb.enterprise.prioritize.model.skill.SkillRecord;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceRepository;
import de.hallerweb.enterprise.prioritize.repository.skill.SkillCategoryRepository;
import de.hallerweb.enterprise.prioritize.repository.skill.SkillRecordRepository;
import de.hallerweb.enterprise.prioritize.repository.skill.SkillRepository;
import de.hallerweb.enterprise.prioritize.service.security.AuthorizationService;
import de.hallerweb.enterprise.prioritize.service.security.UserService; // Import deines UserServices
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
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
    private final AuthorizationService authService;


    private final EntityManager entityManager;

    // --- GLOBALE SKILLS & KATEGORIEN ---

    @Transactional(readOnly = true)
    public List<Skill> getAllSkills() {
        return skillRepository.findAll();
    }

    @Transactional
    public Skill createSkill(Skill skill, PUser user) {
        if (!authService.hasPermission(user, skill, Action.CREATE)) {
            throw new AccessDeniedException("No permission to create a skill.");
        }
        if (skill.getCategory() != null && skill.getCategory().getId() != null) {
            SkillCategory category = skillCategoryRepository.findById(skill.getCategory().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Category with id " + skill.getCategory().getId() + " not found"));
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
        // 1. Load the actual user via your UserService
        PUser user = userService.getUserById(userId);
        record.setUser(user);

        // 2. Verify the global skill
        Skill skill = skillRepository.findById(record.getSkill().getId())
                .orElseThrow(() -> new EntityNotFoundException("Skill not found"));
        record.setSkill(skill);

        // We check whether the user already has a record for THIS specific skill
        if (user.getSkills() != null) {
            boolean skillAlreadyAssigned = user.getSkills().stream()
                    .anyMatch(existingRecord -> existingRecord.getSkill().getId().equals(skill.getId()));

            if (skillAlreadyAssigned) {
                throw new IllegalArgumentException("This skill has already been assigned to the user.");
                // Alternatively you can throw a custom exception, e.g. SkillAlreadyAssignedException
            }
        }


        if (user.getSkills() != null) {
            user.getSkills().add(record);
        }

        return skillRecordRepository.save(record);
    }

    // --- SKILL RECORDS (RESOURCE ASSIGNMENT) ---

    @Transactional(readOnly = true)
    public Set<SkillRecord> getSkillsForResource(Long resourceId) {
        return skillRecordRepository.findByResourceId(resourceId);
    }

    @Transactional
    public SkillRecord assignSkillToResource(Long resourceId, SkillRecord record) {
        // 1. Load resource
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new EntityNotFoundException("Resource with id " + resourceId + " not found"));
        record.setResource(resource);

        // 2. Skill verifizieren
        Skill skill = skillRepository.findById(record.getSkill().getId())
                .orElseThrow(() -> new EntityNotFoundException("Skill not found"));
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
                .orElseThrow(() -> new EntityNotFoundException("Category with id " + categoryId + " not found"));
    }

    @Transactional
    public SkillCategory updateCategory(Long categoryId, SkillCategory categoryDetails) {
        SkillCategory existing = getCategoryById(categoryId);
        existing.setName(categoryDetails.getName());
        existing.setDescription(categoryDetails.getDescription());

        if (categoryDetails.getParentCategory() != null && categoryDetails.getParentCategory().getId() != null) {
            SkillCategory parent = skillCategoryRepository.findById(categoryDetails.getParentCategory().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Parent category with id " + categoryDetails.getParentCategory().getId() + " not found"));
            existing.setParentCategory(parent);
        } else {
            existing.setParentCategory(null);
        }

        return skillCategoryRepository.save(existing);
    }

    @Transactional
    public void deleteSkill(Long skillId, PUser user) {
        Skill skill = getSkillByIdInternal(skillId);
        if (!authService.hasPermission(user, skill, Action.DELETE)) {
            throw new AccessDeniedException("No permission to delete this skill.");
        }
        skillRepository.deleteById(skillId);
    }

    @Transactional
    public void deleteCategory(Long categoryId) {
        // 1. Check whether the category to be deleted even exists
        if (!skillCategoryRepository.existsById(categoryId)) {
            throw new EntityNotFoundException("Category with id " + categoryId + " not found");
        }

        // 2. STEP 1: Find all IDs in the subtree directly at the DB level with a recursive query (PostgreSQL CTE)
        String cteQuery =
                "WITH RECURSIVE subcategories(id) AS (" +  // <-- Added the (id) here for H2 compatibility!
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

            // 3. STEP 2: Detach all affected skills from these categories (null the foreign keys)
            entityManager.createNativeQuery("UPDATE skill SET category_id = NULL WHERE category_id IN :ids")
                    .setParameter("ids", allCategoryIdsToDelete)
                    .executeUpdate();

            // 4. STEP 3: Dissolve the parent-child relationships within the tree
            entityManager.createNativeQuery("UPDATE skill_category SET parent_category_id = NULL WHERE id IN :ids")
                    .setParameter("ids", allCategoryIdsToDelete)
                    .executeUpdate();

            // 5. STEP 4: Now remove the entire branch without residue
            entityManager.createNativeQuery("DELETE FROM skill_category WHERE id IN :ids")
                    .setParameter("ids", allCategoryIdsToDelete)
                    .executeUpdate();
        }

        // 6. Clear the Hibernate cache completely, so that the deleted entities no longer exist in RAM
        entityManager.clear();
    }

    /**
     * Opens an isolated transaction, sets all foreign keys to NULL,
     * persists this to the DB and closes the transaction again immediately.
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
            // Since the collection is already fully in RAM via findAllWithSubCategories(),
            // this loop no longer triggers any mutating DB reloading!
            for (SkillCategory sub : category.getSubCategories()) {
                collectCategoryIdsInMemoryRecursive(sub, ids);
            }
        }
    }

    @Transactional(readOnly = true)
    public Skill getSkillById(Long skillId, PUser user) {
        Skill skill = getSkillByIdInternal(skillId);
        if (!authService.hasPermission(user, skill, Action.READ)) {
            throw new AccessDeniedException("No read permission for this skill.");
        }
        return skill;
    }

    /**
     * Internal variant without permission check – only for other service methods
     * that have already performed their own check.
     */
    @Transactional(readOnly = true)
    Skill getSkillByIdInternal(Long skillId) {
        return skillRepository.findById(skillId)
                .orElseThrow(() -> new EntityNotFoundException("Skill with id " + skillId + " not found"));
    }

    @Transactional
    public Skill updateSkill(Long skillId, Skill skillDetails, PUser user) {
        Skill existing = getSkillByIdInternal(skillId);
        if (!authService.hasPermission(user, existing, Action.UPDATE)) {
            throw new AccessDeniedException("No permission to update this skill.");
        }
        existing.setName(skillDetails.getName());

        if (skillDetails.getCategory() != null && skillDetails.getCategory().getId() != null) {
            SkillCategory category = skillCategoryRepository.findById(skillDetails.getCategory().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Category with id " + skillDetails.getCategory().getId() + " not found"));
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