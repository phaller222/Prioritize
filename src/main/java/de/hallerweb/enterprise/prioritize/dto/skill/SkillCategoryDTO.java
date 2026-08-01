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

import de.hallerweb.enterprise.prioritize.model.skill.SkillCategory;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Summary of a {@link SkillCategory} for the admin categories grid, the parent/category selectors and the
 * REST skills API. Flattens the lazy {@code parentCategory} to id/name (inside the service transaction, or
 * under open-in-view for the single-entity REST responses): {@code SkillCategory}'s all-fields
 * {@code equals}/{@code hashCode} (callSuper) touches its lazy {@code parentCategory}/{@code subCategories},
 * which would throw a {@code LazyInitializationException} inside a Vaadin grid/ComboBox key mapper or during
 * serialization.
 */
@Data
@AllArgsConstructor
public class SkillCategoryDTO {
    private Long id;
    private String name;
    private String description;
    private Long parentId;
    private String parentName;

    /** Maps an entity to its DTO. Reads the lazy {@code parentCategory} (safe inside a tx / under open-in-view). */
    public static SkillCategoryDTO from(SkillCategory category) {
        return new SkillCategoryDTO(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getParentCategory() != null ? category.getParentCategory().getId() : null,
                category.getParentCategory() != null ? category.getParentCategory().getName() : null);
    }
}
