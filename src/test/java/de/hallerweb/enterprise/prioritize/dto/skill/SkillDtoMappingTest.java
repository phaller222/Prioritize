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
import de.hallerweb.enterprise.prioritize.model.skill.SkillRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests (no Spring context) for the request/response DTO mapping of the skill subsystem. They
 * pin that the writable base data is carried across, that a request references its associations by id only
 * (a stub carrying just the id, no full graph), and that the experimental property collections are left
 * untouched (null) rather than wiped.
 */
class SkillDtoMappingTest {

    @Test
    @DisplayName("SkillRequest.toSkill sets base data + a category stub, leaves properties null")
    void skillRequestToSkill() {
        Skill s = new SkillRequest("Welding", "arc welding", "metal,weld", 42L).toSkill();
        assertNull(s.getId());
        assertEquals("Welding", s.getName());
        assertEquals("arc welding", s.getDescription());
        assertEquals("metal,weld", s.getKeywords());
        assertEquals(42L, s.getCategory().getId(), "category is referenced by id only");
        assertNull(s.getSkillProperties(), "experimental property-defs must stay untouched (null), not wiped");
    }

    @Test
    @DisplayName("SkillRequest.toSkill with no category leaves category null")
    void skillRequestNoCategory() {
        assertNull(new SkillRequest("Welding", null, null, null).toSkill().getCategory());
    }

    @Test
    @DisplayName("SkillCategoryRequest.toCategory sets base data + a parent stub")
    void skillCategoryRequestToCategory() {
        SkillCategory c = new SkillCategoryRequest("Trades", "manual", 7L).toCategory();
        assertNull(c.getId());
        assertEquals("Trades", c.getName());
        assertEquals("manual", c.getDescription());
        assertEquals(7L, c.getParentCategory().getId());

        assertNull(new SkillCategoryRequest("Root", null, null).toCategory().getParentCategory());
    }

    @Test
    @DisplayName("SkillRecordRequest.toSkillRecord sets a skill stub + enthusiasm, leaves properties null")
    void skillRecordRequestToRecord() {
        SkillRecord r = new SkillRecordRequest(5L, 8).toSkillRecord();
        assertNull(r.getId());
        assertEquals(5L, r.getSkill().getId());
        assertEquals(8, r.getEnthusiasm());
        assertNull(r.getSkillRecordProperties(), "experimental property values must stay out of the payload");
    }

    @Test
    @DisplayName("SkillSummaryDTO.from carries scalars + category id/name, null-safe")
    void skillSummaryFrom() {
        SkillCategory cat = SkillCategory.builder().name("Trades").build();
        cat.setId(7L);
        Skill s = Skill.builder().name("Welding").description("d").keywords("k").build();
        s.setId(5L);
        s.setCategory(cat);

        SkillSummaryDTO dto = SkillSummaryDTO.from(s);
        assertEquals(5L, dto.getId());
        assertEquals("Welding", dto.getName());
        assertEquals(7L, dto.getCategoryId());
        assertEquals("Trades", dto.getCategoryName());

        Skill orphan = Skill.builder().name("Loose").build();
        assertNull(SkillSummaryDTO.from(orphan).getCategoryId());
        assertNull(SkillSummaryDTO.from(orphan).getCategoryName());
    }

    @Test
    @DisplayName("SkillCategoryDTO.from carries scalars + parent id/name, null-safe")
    void skillCategoryFrom() {
        SkillCategory parent = SkillCategory.builder().name("Trades").build();
        parent.setId(7L);
        SkillCategory child = SkillCategory.builder().name("Welding").description("d").build();
        child.setId(9L);
        child.setParentCategory(parent);

        SkillCategoryDTO dto = SkillCategoryDTO.from(child);
        assertEquals(9L, dto.getId());
        assertEquals("Welding", dto.getName());
        assertEquals(7L, dto.getParentId());
        assertEquals("Trades", dto.getParentName());

        assertNull(SkillCategoryDTO.from(parent).getParentId());
    }

    @Test
    @DisplayName("SkillRecordDTO.from carries skill id/name + enthusiasm, null-safe")
    void skillRecordFrom() {
        Skill skill = Skill.builder().name("Welding").build();
        skill.setId(5L);
        SkillRecord r = SkillRecord.builder().enthusiasm(8).build();
        r.setId(3L);
        r.setSkill(skill);

        SkillRecordDTO dto = SkillRecordDTO.from(r);
        assertEquals(3L, dto.id());
        assertEquals(5L, dto.skillId());
        assertEquals("Welding", dto.skillName());
        assertEquals(8, dto.enthusiasm());

        SkillRecord noSkill = SkillRecord.builder().enthusiasm(1).build();
        assertNull(SkillRecordDTO.from(noSkill).skillId());
        assertTrue(SkillRecordDTO.from(noSkill).enthusiasm() == 1);
    }
}
