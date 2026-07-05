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

package de.hallerweb.enterprise.prioritize.model.project.goal;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.hallerweb.enterprise.prioritize.model.PObject;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Descriptive property of a {@link ProjectGoal}, e.g. a numeric target range or a required
 * document tag. Properties document what a goal is about; they do <em>not</em> drive progress,
 * which is derived from the goal's tasks.
 * <p>
 * Concrete flavours are {@link ProjectGoalPropertyNumeric} and {@link ProjectGoalPropertyDocument}.
 * The whole hierarchy lives in a single table ({@link InheritanceType#SINGLE_TABLE}) because the
 * {@code IDENTITY} id generation inherited from {@link PObject} rules out {@code TABLE_PER_CLASS}.
 *
 * @author peter haller
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "property_type")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ProjectGoalPropertyNumeric.class, name = "numeric"),
    @JsonSubTypes.Type(value = ProjectGoalPropertyDocument.class, name = "document")
})
public abstract class ProjectGoalProperty extends PObject {

    private String name;
    private String description;
}
