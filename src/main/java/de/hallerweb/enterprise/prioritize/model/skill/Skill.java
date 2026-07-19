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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.security.PAuthorizedObject;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Builder
public class Skill extends PObject implements PAuthorizedObject {

    @ToString.Include
    private String name;

    @Column(length = 1024)
    private String description;

    private String keywords;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    // When a skill is serialized, its category is emitted as a flat {id, name, description} only: the lazy
    // subCategories/parentCategory tree must NOT be walked here. Doing so lazy-initialises the collection while
    // Jackson iterates it (under open-in-view), which throws a ConcurrentModificationException and turns
    // GET /api/v1/skills into a 403. The dedicated category endpoint keeps the full tree (it eager-fetches it).
    @JsonIgnoreProperties({"subCategories", "parentCategory"})
    private SkillCategory category;

    @Builder.Default
    @OneToMany(mappedBy = "skill", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference(value = "skill-properties")
    private Set<SkillProperty> skillProperties = new HashSet<>();

    // Helper for the bidirectional association (important for JPA!)
    public void addSkillProperty(SkillProperty property) {
        skillProperties.add(property);
        property.setSkill(this);
    }

    public void removeSkillProperty(SkillProperty property) {
        skillProperties.remove(property);
        property.setSkill(null);
    }

    @Override
    public String toString() {
        return name;
    }
}