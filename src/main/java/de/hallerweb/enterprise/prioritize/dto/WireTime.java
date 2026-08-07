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

package de.hallerweb.enterprise.prioritize.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Converts between the entities' server-zone {@link LocalDateTime} and the {@link Instant} the REST API
 * puts on the wire.
 * <p>
 * <b>Why this exists:</b> OpenAPI's {@code format: date-time} is RFC 3339, which <em>requires</em> a UTC
 * offset. Jackson serializes a {@code LocalDateTime} without one ({@code "2026-08-02T21:40:20.81039"}),
 * so every generated, statically typed client — which maps {@code date-time} to {@code OffsetDateTime} —
 * failed to parse those fields, while the {@code Instant}-backed fields on the same API parsed fine.
 * Mapping to {@code Instant} at the DTO boundary makes the emitted JSON match the published contract.
 * Entities keep their {@code LocalDateTime} columns; nothing about persistence changes.
 * <p>
 * <b>Zone:</b> every {@code LocalDateTime} in the model is server-zone wall clock — it originates from
 * {@code LocalDateTime.now()}, and {@code TaskSchedule.nextFireAt} is explicitly normalized to the server
 * zone by {@code TaskScheduleService}. {@link ZoneId#systemDefault()} is therefore the correct — and only
 * correct — interpretation of the stored values.
 *
 * @author peter haller
 */
public final class WireTime {

    private WireTime() {
        // utility class
    }

    /**
     * Interprets a stored server-zone timestamp as the instant it denotes.
     *
     * @param local the entity's wall-clock value, may be {@code null}
     * @return the corresponding instant, or {@code null} if {@code local} was {@code null}
     */
    public static Instant toInstant(LocalDateTime local) {
        return local == null ? null : local.atZone(ZoneId.systemDefault()).toInstant();
    }

    /**
     * Converts an instant received from a client back to the server-zone wall clock the entities store.
     *
     * @param instant the instant off the wire, may be {@code null}
     * @return the corresponding server-zone timestamp, or {@code null} if {@code instant} was {@code null}
     */
    public static LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
