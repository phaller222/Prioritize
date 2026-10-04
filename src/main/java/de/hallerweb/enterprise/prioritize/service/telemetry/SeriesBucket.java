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

package de.hallerweb.enterprise.prioritize.service.telemetry;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * The time slots a measurement series is grouped into. Slots are calendar slots in the requested time
 * zone (a {@link #DAY} is a local day, which matters for "consumption per day"), {@link #WEEK}s start on
 * Monday (ISO 8601). {@link #NONE} returns every reading unaggregated.
 *
 * @author peter haller
 */
public enum SeriesBucket {

    NONE, MINUTE, HOUR, DAY, WEEK, MONTH;

    /**
     * The start of the slot {@code time} falls into. Undefined for {@link #NONE}, which has no slots.
     */
    ZonedDateTime startOf(ZonedDateTime time) {
        return switch (this) {
            case MINUTE -> time.truncatedTo(ChronoUnit.MINUTES);
            case HOUR -> time.truncatedTo(ChronoUnit.HOURS);
            case DAY -> time.truncatedTo(ChronoUnit.DAYS);
            case WEEK -> time.truncatedTo(ChronoUnit.DAYS)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> time.truncatedTo(ChronoUnit.DAYS).withDayOfMonth(1);
            case NONE -> throw new IllegalStateException("NONE has no slots.");
        };
    }
}
