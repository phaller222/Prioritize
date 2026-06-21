package de.hallerweb.enterprise.prioritize.repository.skill;

import de.hallerweb.enterprise.prioritize.model.skill.SkillCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SkillCategoryRepository extends JpaRepository<SkillCategory, Long> {
    SkillCategory findByName(String name);

    /**
     * Loads all skill categories and eagerly pulls their subcategories
     * in the same DB call (eager loading via JOIN FETCH).
     * This prevents a ConcurrentModificationException in the service.
     */
    @Query("SELECT DISTINCT c FROM SkillCategory c LEFT JOIN FETCH c.subCategories")
    List<SkillCategory> findAllWithSubCategories();
}