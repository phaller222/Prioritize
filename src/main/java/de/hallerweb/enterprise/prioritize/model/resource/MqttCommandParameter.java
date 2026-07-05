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

package de.hallerweb.enterprise.prioritize.model.resource;

import de.hallerweb.enterprise.prioritize.model.PObject;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * A single parameter of an {@link MqttCommand}, declared by a device during discovery.
 * Carries the parameter's type and optional constraints so a UI can render an
 * appropriate input and (later) validate values before the command is sent.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class MqttCommandParameter extends PObject {

    /** Parameter name, unique within its command (e.g. {@code level}). */
    @EqualsAndHashCode.Include
    private String name;

    @Enumerated(EnumType.STRING)
    private MqttParameterType type;

    /** Inclusive lower bound for numeric types; {@code null} if unconstrained. */
    private Double minValue;

    /** Inclusive upper bound for numeric types; {@code null} if unconstrained. */
    private Double maxValue;

    /** Optional unit shown next to the value (e.g. {@code %}, {@code °C}). */
    private String unit;

    /** Whether the caller must supply this parameter. */
    private boolean required;

    /** Allowed values for {@link MqttParameterType#ENUM}; empty otherwise. */
    @ElementCollection
    private Set<String> allowedValues = new HashSet<>();
}
