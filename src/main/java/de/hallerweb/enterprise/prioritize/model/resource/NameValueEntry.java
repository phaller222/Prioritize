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
import jakarta.persistence.Entity;
import lombok.*;

/**
 * Represents MQTT IoT data (name-value pairs).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
@ToString(onlyExplicitlyIncluded = true)
public class NameValueEntry extends PObject implements Comparable<NameValueEntry> {

    @ToString.Include
    private String mqttName; // Name of the data point

    private String mqttValues; // Comma-separated values (historical)

    @Override
    public int compareTo(NameValueEntry other) {
        if (this.mqttName == null || other.getMqttName() == null) {
            return 0;
        }
        return this.mqttName.compareTo(other.getMqttName());
    }
}