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

import de.hallerweb.enterprise.prioritize.model.resource.ResourceReservation;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * A single reservation of a resource for the admin occupancy overview (display + cancel only) and the REST
 * reservations API. Flattened out of {@link ResourceReservation} and its lazy {@code reservedBy} /
 * {@code timespan} (inside the service transaction, or under open-in-view for the REST responses), so a
 * consumer never touches those detached relations.
 */
@Data
@AllArgsConstructor
public class ResourceReservationDTO {
    private Integer id;
    private String reservedBy;
    private Instant from;
    private Instant until;
    private int slotNumber;

    /** Maps an entity to its DTO. Reads the lazy {@code reservedBy}/{@code timespan} (safe inside a tx / under open-in-view). */
    public static ResourceReservationDTO from(ResourceReservation reservation) {
        return new ResourceReservationDTO(
                reservation.getId(),
                reservation.getReservedBy() != null ? reservation.getReservedBy().getUsername() : null,
                reservation.getTimespan() != null ? reservation.getTimespan().getDateFrom() : null,
                reservation.getTimespan() != null ? reservation.getTimespan().getDateUntil() : null,
                reservation.getSlotNumber());
    }
}
