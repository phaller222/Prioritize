package de.hallerweb.enterprise.prioritize.repository.skill;

import de.hallerweb.enterprise.prioritize.model.skill.Skill;
import de.hallerweb.enterprise.prioritize.model.skill.SkillCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SkillRepository extends JpaRepository<Skill, Long> {
    Skill findByName(String name);

    @Query("SELECT DISTINCT c FROM SkillCategory c LEFT JOIN FETCH c.subCategories")
    List<SkillCategory> findAllWithSubCategories();
}