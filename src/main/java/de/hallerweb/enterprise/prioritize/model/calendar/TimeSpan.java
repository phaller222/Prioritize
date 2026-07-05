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

package de.hallerweb.enterprise.prioritize.model.calendar;

import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.PAuthorizedObject;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * Time span that is reserved for a specific resource or used for other purposes.
 */
public class TimeSpan implements PAuthorizedObject {

    public enum TimeSpanType {
        RESOURCE_RESERVATION, VACATION, ILLNESS, TIME_TRACKER, OTHER, ALL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Implementiert PAuthorizedObject.getId() via Lombok @Getter

    private String title;
    private String description;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "timespan_resources",
        joinColumns = @JoinColumn(name = "timespan_id"),
        inverseJoinColumns = @JoinColumn(name = "resource_id")
    )
    private Set<Resource> involvedResources = new HashSet<>();

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "timespan_users",
        joinColumns = @JoinColumn(name = "timespan_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<PUser> involvedUsers = new HashSet<>();

    private Instant dateFrom;
    private Instant dateUntil;

    @Enumerated(EnumType.STRING)
    private TimeSpanType type;

    // --- Business Logik ---

    /**
     * Checks whether this time span overlaps with another time span.
     *
     * @param other The other time span to check against
     * @return true if the time spans overlap, false otherwise
     */
    public boolean intersects(TimeSpan other) {
        if (other == null || other.getDateFrom() == null || other.getDateUntil() == null) {
            return false;
        }
        return !dateFrom.isAfter(other.getDateUntil()) && !dateUntil.isBefore(other.getDateFrom());
    }

    @Override
    public Long getId() {
        return this.id;
    }
}