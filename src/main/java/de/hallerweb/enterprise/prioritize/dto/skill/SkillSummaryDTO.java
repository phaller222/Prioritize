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
 * Summary of a {@link de.hallerweb.enterprise.prioritize.model.skill.Skill} for the admin skills grid and the
 * category selector. Carries only scalar fields plus the category id/name (resolved inside the service
 * transaction): the {@code Skill} entity has a degenerate {@code equals}/{@code hashCode}
 * ({@code onlyExplicitlyIncluded} with no included fields → every skill "equal") and a lazy {@code category},
 * so it must never sit in a Vaadin grid or ComboBox directly.
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
}
