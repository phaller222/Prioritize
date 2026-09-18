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

import de.hallerweb.enterprise.prioritize.config.AuthenticatedUser;
import de.hallerweb.enterprise.prioritize.dto.security.UserDTO;
import de.hallerweb.enterprise.prioritize.dto.skill.QualificationLevelDTO;
import de.hallerweb.enterprise.prioritize.dto.skill.QualificationLevelRequest;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.skill.QualificationLevel;
import de.hallerweb.enterprise.prioritize.service.skill.QualificationLevelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Qualification levels",
        description = "What an hour of work is costed at, by qualification rather than by person.")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class QualificationLevelController {

    private final QualificationLevelService qualificationLevelService;

    @Operation(summary = "Get all qualification levels")
    @GetMapping("/qualification-levels")
    public ResponseEntity<List<QualificationLevelDTO>> getAllQualificationLevels(@AuthenticatedUser PUser currentUser) {
        List<QualificationLevelDTO> levels = qualificationLevelService.getAllQualificationLevels(currentUser).stream()
                .map(QualificationLevelDTO::from)
                .toList();
        return ResponseEntity.ok(levels);
    }

    @Operation(summary = "Get qualification level by id")
    @GetMapping("/qualification-levels/{levelId}")
    public ResponseEntity<QualificationLevelDTO> getQualificationLevelById(@PathVariable Long levelId,
                                                                          @AuthenticatedUser PUser currentUser) {
        QualificationLevel level = qualificationLevelService.getQualificationLevelById(levelId, currentUser);
        return ResponseEntity.ok(QualificationLevelDTO.from(level));
    }

    @Operation(summary = "Create qualification level")
    @PostMapping("/qualification-levels")
    public ResponseEntity<QualificationLevelDTO> createQualificationLevel(@RequestBody QualificationLevelRequest request,
                                                                         @AuthenticatedUser PUser currentUser) {
        QualificationLevel created =
                qualificationLevelService.createQualificationLevel(request.toQualificationLevel(), currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(QualificationLevelDTO.from(created));
    }

    @Operation(summary = "Update qualification level",
            description = "Replaces the level. Omitting the three cost fields clears its cost rate.")
    @PutMapping("/qualification-levels/{levelId}")
    public ResponseEntity<QualificationLevelDTO> updateQualificationLevel(@PathVariable Long levelId,
                                                                         @RequestBody QualificationLevelRequest request,
                                                                         @AuthenticatedUser PUser currentUser) {
        QualificationLevel updated = qualificationLevelService.updateQualificationLevel(
                levelId, request.toQualificationLevel(), currentUser);
        return ResponseEntity.ok(QualificationLevelDTO.from(updated));
    }

    @Operation(summary = "Delete qualification level",
            description = "Refused while people are still assigned to the level.")
    @DeleteMapping("/qualification-levels/{levelId}")
    public ResponseEntity<Void> deleteQualificationLevel(@PathVariable Long levelId,
                                                        @AuthenticatedUser PUser currentUser) {
        qualificationLevelService.deleteQualificationLevel(levelId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Assign a user to a qualification level",
            description = "Omit levelId to take the user off their qualification level.")
    @PutMapping("/users/{userId}/qualification-level")
    public ResponseEntity<UserDTO> assignQualificationLevel(@PathVariable Long userId,
                                                            @RequestParam(required = false) Long levelId,
                                                            @AuthenticatedUser PUser currentUser) {
        PUser updated = qualificationLevelService.assignQualificationLevel(userId, levelId, currentUser);
        return ResponseEntity.ok(UserDTO.from(updated));
    }
}
