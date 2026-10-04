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

package de.hallerweb.enterprise.prioritize.dto.telemetry;

import de.hallerweb.enterprise.prioritize.service.telemetry.SeriesAggregation;
import de.hallerweb.enterprise.prioritize.service.telemetry.SeriesBucket;
import java.time.Instant;
import java.util.List;

/**
 * A measurement series of one telemetry data point over a time window, either raw or grouped into
 * calendar slots. Echoes the effective request parameters, so a caller that relied on the defaults
 * still learns which window and grouping it got.
 *
 * @param resourceId  the resource the data point belongs to
 * @param datapoint   the data point name
 * @param from        start of the window (inclusive)
 * @param to          end of the window (exclusive)
 * @param bucket      the slot size; {@code NONE} for raw readings
 * @param aggregation how each slot was reduced; ignored (but echoed) for {@code NONE}
 * @param zone        the time zone the slots were cut in
 * @param points      the series, oldest first; empty slots are omitted
 */
public record TelemetrySeriesDTO(
        Long resourceId,
        String datapoint,
        Instant from,
        Instant to,
        SeriesBucket bucket,
        SeriesAggregation aggregation,
        String zone,
        List<Point> points) {

    /**
     * One point of a series.
     *
     * @param at      the reading's time, or the start of the slot
     * @param value   the reading, or the slot's aggregated value
     * @param samples how many readings the point stands for ({@code 1} for raw readings) — a slot with
     *                few samples marks a gap in the recording
     */
    public record Point(Instant at, double value, int samples) {
    }
}
