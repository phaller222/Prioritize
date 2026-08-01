/*
 * Copyright 2026 Peter Michael Haller and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.hallerweb.enterprise.prioritize.controller.skill;

import de.hallerweb.enterprise.prioritize.config.CurrentUserResolver;
import de.hallerweb.enterprise.prioritize.dto.skill.SkillCategoryDTO;
import de.hallerweb.enterprise.prioritize.dto.skill.SkillCategoryRequest;
import de.hallerweb.enterprise.prioritize.dto.skill.SkillRequest;
import de.hallerweb.enterprise.prioritize.dto.skill.SkillSummaryDTO;
import de.hallerweb.enterprise.prioritize.model.skill.Skill;
import de.hallerweb.enterprise.prioritize.model.skill.SkillCategory;
import de.hallerweb.enterprise.prioritize.service.skill.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

@Tag(name = "Skills", description = "Manage skills and skill categories.")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;
    private final CurrentUserResolver currentUserResolver;

    // ==========================================
    // 1. GLOBALES SKILL-VERZEICHNIS & KATEGORIEN
    // ==========================================

    @Operation(summary = "Get all skills")
    @GetMapping("/skills")
    public ResponseEntity<List<SkillSummaryDTO>> getAllSkills() {
        return ResponseEntity.ok(skillService.getAllSkillSummaries());
    }

    @Operation(summary = "Create skill")
    @PostMapping("/skills")
    public ResponseEntity<SkillSummaryDTO> createSkill(@RequestBody SkillRequest request, Authentication auth) {
        Skill createdSkill = skillService.createSkill(request.toSkill(), currentUserResolver.resolve(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(SkillSummaryDTO.from(createdSkill));
    }

    @Operation(summary = "Get skill by id")
    @GetMapping("/skills/{skillId}")
    public ResponseEntity<SkillSummaryDTO> getSkillById(@PathVariable Long skillId, Authentication auth) {
        Skill skill = skillService.getSkillById(skillId, currentUserResolver.resolve(auth));
        return ResponseEntity.ok(SkillSummaryDTO.from(skill));
    }

    @Operation(summary = "Update skill")
    @PutMapping("/skills/{skillId}")
    public ResponseEntity<SkillSummaryDTO> updateSkill(
            @PathVariable Long skillId,
            @RequestBody SkillRequest request,
            Authentication auth) {
        Skill updated = skillService.updateSkill(skillId, request.toSkill(), currentUserResolver.resolve(auth));
        return ResponseEntity.ok(SkillSummaryDTO.from(updated));
    }

    @Operation(summary = "Get all categories")
    @GetMapping("/skills/categories")
    public ResponseEntity<List<SkillCategoryDTO>> getAllCategories() {
        return ResponseEntity.ok(skillService.getAllCategorySummaries());
    }

    @Operation(summary = "Create category")
    @PostMapping("/skills/categories")
    public ResponseEntity<SkillCategoryDTO> createCategory(@RequestBody SkillCategoryRequest request) {
        SkillCategory createdCategory = skillService.createCategory(request.toCategory());
        return ResponseEntity.status(HttpStatus.CREATED).body(SkillCategoryDTO.from(createdCategory));
    }

    @Operation(summary = "Get category by id")
    @GetMapping("/skills/categories/{categoryId}")
    public ResponseEntity<SkillCategoryDTO> getCategoryById(@PathVariable Long categoryId) {
        return ResponseEntity.ok(SkillCategoryDTO.from(skillService.getCategoryById(categoryId)));
    }

    @Operation(summary = "Update category")
    @PutMapping("/skills/categories/{categoryId}")
    public ResponseEntity<SkillCategoryDTO> updateCategory(
            @PathVariable Long categoryId,
            @RequestBody SkillCategoryRequest request) {
        return ResponseEntity.ok(SkillCategoryDTO.from(skillService.updateCategory(categoryId, request.toCategory())));
    }

    // ==========================================
    // DELETE ENDPOINTS
    // ==========================================

    @Operation(summary = "Delete skill")
    @DeleteMapping("/skills/{skillId}")
    public ResponseEntity<Void> deleteSkill(@PathVariable Long skillId, Authentication auth) {
        skillService.deleteSkill(skillId, currentUserResolver.resolve(auth));
        return ResponseEntity.noContent().build(); // 204 No Content is standard for a successful delete
    }

    @Operation(summary = "Delete category")
    @DeleteMapping("/skills/categories/{categoryId}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long categoryId) {
        skillService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }

}