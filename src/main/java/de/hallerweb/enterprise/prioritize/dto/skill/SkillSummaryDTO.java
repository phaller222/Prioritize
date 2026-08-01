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

package de.hallerweb.enterprise.prioritize.dto.skill;

import de.hallerweb.enterprise.prioritize.model.skill.Skill;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Summary of a {@link Skill} for the admin skills grid, the category selector and the REST skills API.
 * Carries only scalar fields plus the category id/name (resolved inside the service transaction, or under
 * open-in-view for the single-entity REST responses): the {@code Skill} entity has a degenerate
 * {@code equals}/{@code hashCode} ({@code onlyExplicitlyIncluded} with no included fields → every skill
 * "equal") and a lazy {@code category}, so it must never sit in a Vaadin grid or ComboBox directly, nor be
 * serialized straight onto the wire.
 */
@Data
@AllArgsConstructor
public class SkillSummaryDTO {
    private Long id;
    private String name;
    private String description;
    private String keywords;
    private Long categoryId;
    private String categoryName;

    /** Maps an entity to its DTO. Reads the lazy {@code category} (safe inside a tx / under open-in-view). */
    public static SkillSummaryDTO from(Skill skill) {
        return new SkillSummaryDTO(
                skill.getId(),
                skill.getName(),
                skill.getDescription(),
                skill.getKeywords(),
                skill.getCategory() != null ? skill.getCategory().getId() : null,
                skill.getCategory() != null ? skill.getCategory().getName() : null);
    }
}
