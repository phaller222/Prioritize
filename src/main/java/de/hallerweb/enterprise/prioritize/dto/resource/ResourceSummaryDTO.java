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

package de.hallerweb.enterprise.prioritize.dto.resource;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Summary of a {@link de.hallerweb.enterprise.prioritize.model.resource.Resource} for the admin resources grid.
 * Carries only scalar fields so it can safely live inside a Vaadin grid's key mapper — the {@code Resource}
 * entity's all-fields {@code equals}/{@code hashCode} would touch its lazy relations there and throw a
 * {@code LazyInitializationException}. {@code occupiedSlots} is the number of reservations overlapping "now",
 * computed against {@code maxSlots} for the occupancy display; the online fields drive the green/grey status dot.
 */
@Data
@AllArgsConstructor
public class ResourceSummaryDTO {
    private Long id;
    private String name;
    private String description;
    private boolean mqttResource;
    private boolean mqttOnline;
    private LocalDateTime mqttLastPing;
    private int maxSlots;
    private int occupiedSlots;
}
