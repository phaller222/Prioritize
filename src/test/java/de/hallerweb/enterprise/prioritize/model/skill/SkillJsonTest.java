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

package de.hallerweb.enterprise.prioritize.model.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the serialization contract behind the GET /api/v1/skills fix: a {@link Skill} must emit its
 * {@link SkillCategory} as a flat object (id/name/description) and must NOT walk the category's
 * {@code subCategories}/{@code parentCategory} tree. Under Hibernate + open-in-view, walking that lazy tree
 * threw a {@code ConcurrentModificationException} during JSON writing, surfacing as a 403 on GET /skills. The
 * {@code @JsonIgnoreProperties} on {@link Skill#getCategory()} prevents it; this test locks that behaviour in so
 * the annotation cannot be dropped unnoticed. (The pure-POJO graph here cannot reproduce the Hibernate lazy-init
 * itself — the end-to-end fix is verified against the running app.)
 */
class SkillJsonTest {

    @Test
    @DisplayName("Skill JSON: die Kategorie wird flach serialisiert, ohne subCategories-Baum")
    void skillCategorySerializedWithoutSubcategoryTree() throws Exception {
        SkillCategory parent = new SkillCategory();
        parent.setId(1L);
        parent.setName("IT");

        SkillCategory child = new SkillCategory();
        child.setId(2L);
        child.setName("Java");
        child.setParentCategory(parent);
        // new SkillCategory() leaves subCategories null (Lombok @Builder.Default footgun), so seed it explicitly.
        parent.setSubCategories(new HashSet<>());
        parent.getSubCategories().add(child);

        Skill skill = new Skill();
        skill.setId(10L);
        skill.setName("Spring Boot");
        skill.setCategory(parent);

        ObjectMapper mapper = new ObjectMapper();
        String json = assertDoesNotThrow(() -> mapper.writeValueAsString(skill));

        assertTrue(json.contains("\"category\""), "category must still be present");
        assertTrue(json.contains("\"IT\""), "category name must still be serialized");
        assertFalse(json.contains("subCategories"),
                "the category's subCategories tree must not be walked when nested under a skill");
    }
}
