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
import de.hallerweb.enterprise.prioritize.model.skill.SkillCategory;

/**
 * Request body for creating or updating a {@link Skill}. Carries the writable base data plus the target
 * category by id — the service resolves the managed {@link SkillCategory} from it. The experimental,
 * polymorphic {@code skillProperties} (property <em>definitions</em>) are deliberately not part of the
 * stable request: {@link #toSkill()} leaves the collection {@code null} so an update never wipes existing
 * definitions.
 *
 * @author peter haller
 */
public record SkillRequest(String name,
                           String description,
                           String keywords,
                           Long categoryId) {

    /** Builds a fresh, id-less {@link Skill} carrying a category stub (id only), if a category was given. */
    public Skill toSkill() {
        Skill skill = Skill.builder()
                .name(name)
                .description(description)
                .keywords(keywords)
                .skillProperties(null) // experimental property-defs stay out of the stable payload
                .build();
        if (categoryId != null) {
            SkillCategory categoryRef = new SkillCategory();
            categoryRef.setId(categoryId);
            skill.setCategory(categoryRef);
        }
        return skill;
    }
}
