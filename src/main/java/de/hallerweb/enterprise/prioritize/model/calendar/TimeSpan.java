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

import de.hallerweb.enterprise.prioritize.model.cost.CostRateUnit;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.PAuthorizedObject;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
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
        RESOURCE_RESERVATION, VACATION, ILLNESS, TIME_TRACKER, EQUIPMENT_USAGE, OTHER, ALL
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

    // --- Correction audit ---
    // A tracked work session may be fixed after the fact (someone forgets to clock out), but never
    // silently: whoever touches a span leaves their name, the time, a reason and the bounds as they
    // were before. Null on every span that was recorded normally and never touched.
    // The three cases are told apart by which original bound survives:
    //   originalFrom != null && originalUntil != null -> a closed session was corrected
    //   originalFrom != null && originalUntil == null -> a running session was stopped retroactively
    //   originalFrom == null                          -> the session was entered by hand afterwards
    // See TaskService for the operations that set these.

    @ManyToOne(fetch = FetchType.LAZY)
    private PUser correctedBy;

    private Instant correctedAt;
    private Instant originalFrom;
    private Instant originalUntil;
    private String correctionReason;

    // --- Cost rate as it stood when this span was closed ---
    // Stamped once, when a booking stops: what the device cost per hour that day, or what the
    // worker's qualification level was costed at. Null on every span that carries no cost meaning
    // (a reservation, a holiday) and on a span that is still running — a running booking has no
    // final cost yet and is priced live.
    //
    // The point of stamping is that a cost figure stays true. Reading the rate off the resource or
    // the qualification level at report time means every rate change silently rewrites the past:
    // raise the lift's day rate in November and the job you calculated in March reports a different
    // number, with nothing in the data to show why. Stamped, the cost is a fact of the record.
    // Changing a rate then applies to what happens next, which is what everybody assumes anyway.

    @Column(precision = 12, scale = 2)
    private BigDecimal costRate;

    /** ISO-4217 code of {@link #costRate}. */
    private String costCurrency;

    /** Whether {@link #costRate} was charged per hour, per day or per use. */
    @Enumerated(EnumType.STRING)
    private CostRateUnit costRateUnit;

    /**
     * What the rate hung on when it was stamped — the qualification level's name for a work session,
     * the device's name for an equipment booking.
     * <p>
     * A plain string, deliberately not a reference. It is the label a report groups by, and a report
     * about last spring should keep saying "Geselle" even after that person was promoted, the level
     * was renamed, or it was deleted altogether. A foreign key would either follow those changes or
     * block them; a copy of the name does neither.
     */
    private String costRateLabel;

    /** Whether this span carries a rate of its own — a complete one, as {@code CostRateRules} requires. */
    public boolean hasCostRate() {
        return costRate != null && costCurrency != null && costRateUnit != null;
    }

    // --- Business Logik ---

    /** Whether this time span was entered or changed by hand after the fact. */
    public boolean isCorrected() {
        return correctedAt != null;
    }

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