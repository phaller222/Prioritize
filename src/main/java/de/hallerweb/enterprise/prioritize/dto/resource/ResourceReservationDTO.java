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

import java.time.Instant;

/**
 * A single reservation of a resource for the admin occupancy overview (display + cancel only). Flattened out of
 * {@link de.hallerweb.enterprise.prioritize.model.resource.ResourceReservation} and its lazy {@code reservedBy}
 * / {@code timespan} inside the service transaction, so the view never touches those detached relations.
 */
@Data
@AllArgsConstructor
public class ResourceReservationDTO {
    private Integer id;
    private String reservedBy;
    private Instant from;
    private Instant until;
    private int slotNumber;
}
