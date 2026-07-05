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

package de.hallerweb.enterprise.prioritize.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * PActor is the base for all entities that can actively work on tasks
 * (e.g. persons or machines).
 */
@Entity
@Table(name = "pactor")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "actor_type") // Helps Hibernate with the mapping
@Getter
@Setter
@NoArgsConstructor
public abstract class PActor extends PObject {
    // If you later need fields for all actors (e.g. an internal identifier),
    // they go in here.
}