package de.hallerweb.enterprise.prioritize.repository.skill;

import de.hallerweb.enterprise.prioritize.model.skill.SkillRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Set;

@Repository
public interface SkillRecordRepository extends JpaRepository<SkillRecord, Integer> {
    Set<SkillRecord> findByUserId(Long userId);
    Set<SkillRecord> findByResourceId(Long resourceId);
}