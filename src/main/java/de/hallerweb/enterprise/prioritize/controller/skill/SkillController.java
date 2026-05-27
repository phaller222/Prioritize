package de.hallerweb.enterprise.prioritize.controller.skill;

import de.hallerweb.enterprise.prioritize.model.skill.Skill;
import de.hallerweb.enterprise.prioritize.model.skill.SkillCategory;
import de.hallerweb.enterprise.prioritize.service.skill.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    // ==========================================
    // 1. GLOBALES SKILL-VERZEICHNIS & KATEGORIEN
    // ==========================================

    @GetMapping("/skills")
    public ResponseEntity<List<Skill>> getAllSkills() {
        return ResponseEntity.ok(skillService.getAllSkills());
    }

    @PostMapping("/skills")
    public ResponseEntity<Skill> createSkill(@RequestBody Skill skill) {
        Skill createdSkill = skillService.createSkill(skill);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdSkill);
    }

    @GetMapping("/skills/categories")
    public ResponseEntity<List<SkillCategory>> getAllCategories() {
        return ResponseEntity.ok(skillService.getAllCategories());
    }

    @PostMapping("/skills/categories")
    public ResponseEntity<SkillCategory> createCategory(@RequestBody SkillCategory category) {
        SkillCategory createdCategory = skillService.createCategory(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCategory);
    }

    // ==========================================
    // LÖSCH-ENDPUNKTE
    // ==========================================

    @DeleteMapping("/skills/{skillId}")
    public ResponseEntity<Void> deleteSkill(@PathVariable Long skillId) {
        skillService.deleteSkill(skillId);
        return ResponseEntity.noContent().build(); // 204 No Content ist Standard bei erfolgreichem Delete
    }

    @DeleteMapping("skills/categories/{categoryId}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long categoryId) {
        skillService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }

}