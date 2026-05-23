package de.hallerweb.enterprise.prioritize.repository.skill;

import de.hallerweb.enterprise.prioritize.model.skill.SkillCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SkillCategoryRepository extends JpaRepository<SkillCategory, Integer> {
    SkillCategory findByName(String name);

    /**
     * Lädt alle Skill-Kategorien und zieht deren Unterkategorien (subCategories)
     * sofort in demselben DB-Abruf mit (Eager Loading per JOIN FETCH).
     * Das verhindert eine ConcurrentModificationException im Service.
     */
    @Query("SELECT DISTINCT c FROM SkillCategory c LEFT JOIN FETCH c.subCategories")
    List<SkillCategory> findAllWithSubCategories();
}