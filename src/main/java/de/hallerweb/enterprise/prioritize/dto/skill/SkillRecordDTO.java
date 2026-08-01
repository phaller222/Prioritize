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
import de.hallerweb.enterprise.prioritize.model.skill.SkillRecord;

/**
 * Flat, transport-safe view of a {@link SkillRecord} (a skill assigned to a user or resource). Carries the
 * record id, the assigned skill's id/name and the enthusiasm rating. The owning {@code user}/{@code resource}
 * back-references are not exposed (they are {@code @JsonIgnore} on the entity), and neither are the
 * experimental {@code skillRecordProperties}. {@code skill.getName()} is a single lazy relation, safe to
 * read under open-in-view.
 *
 * @author peter haller
 */
public record SkillRecordDTO(Long id,
                             Long skillId,
                             String skillName,
                             Integer enthusiasm) {

    /** Maps an entity to its DTO. */
    public static SkillRecordDTO from(SkillRecord record) {
        Skill skill = record.getSkill();
        return new SkillRecordDTO(
                record.getId(),
                skill != null ? skill.getId() : null,
                skill != null ? skill.getName() : null,
                record.getEnthusiasm());
    }
}
