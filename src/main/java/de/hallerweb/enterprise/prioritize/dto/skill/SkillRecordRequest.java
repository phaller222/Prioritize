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
 * Request body for assigning a {@link Skill} to a user or resource. Identifies the skill by id (the owning
 * user/resource comes from the path) and carries the enthusiasm rating. The experimental, polymorphic
 * {@code skillRecordProperties} (per-record property <em>values</em>) are deliberately not part of the
 * stable request: {@link #toSkillRecord()} leaves the collection {@code null}.
 *
 * @author peter haller
 */
public record SkillRecordRequest(Long skillId,
                                 Integer enthusiasm) {

    /** Builds a fresh, id-less {@link SkillRecord} carrying a skill stub (id only). */
    public SkillRecord toSkillRecord() {
        SkillRecord record = SkillRecord.builder()
                .enthusiasm(enthusiasm)
                .skillRecordProperties(null) // experimental property values stay out of the stable payload
                .build();
        Skill skillRef = new Skill();
        skillRef.setId(skillId);
        record.setSkill(skillRef);
        return record;
    }
}
