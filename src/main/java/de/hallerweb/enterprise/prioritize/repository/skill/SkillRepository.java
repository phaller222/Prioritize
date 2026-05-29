package de.hallerweb.enterprise.prioritize.repository.skill;

import de.hallerweb.enterprise.prioritize.model.skill.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SkillRepository extends JpaRepository<Skill, Long> {
    Skill findByName(String name);

}