package de.hallerweb.enterprise.prioritize;

import de.hallerweb.enterprise.prioritize.model.skill.Skill;
import de.hallerweb.enterprise.prioritize.model.skill.SkillCategory;
import de.hallerweb.enterprise.prioritize.repository.skill.SkillCategoryRepository;
import de.hallerweb.enterprise.prioritize.repository.skill.SkillRepository;
import de.hallerweb.enterprise.prioritize.service.skill.SkillService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("postgres")
class PrioritizeApplicationTests {

    @Test
    void contextLoads() {
        // Dieser Test bleibt leer.
        // Er schlägt automatisch fehl, wenn der Spring-Kontext nicht hochfährt.
    }
}