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

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Summary of a {@link de.hallerweb.enterprise.prioritize.model.skill.SkillCategory} for the admin categories
 * grid and the parent/category selectors. Flattens the lazy {@code parentCategory} to id/name inside the
 * service transaction: {@code SkillCategory}'s all-fields {@code equals}/{@code hashCode} (callSuper) touches
 * its lazy {@code parentCategory}/{@code subCategories}, which would throw a {@code LazyInitializationException}
 * inside a Vaadin grid/ComboBox key mapper.
 */
@Data
@AllArgsConstructor
public class SkillCategoryDTO {
    private Long id;
    private String name;
    private String description;
    private Long parentId;
    private String parentName;
}
