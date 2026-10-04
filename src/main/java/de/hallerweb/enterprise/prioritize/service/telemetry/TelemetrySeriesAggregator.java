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

import de.hallerweb.enterprise.prioritize.dto.telemetry.TelemetrySeriesDTO.Point;
import de.hallerweb.enterprise.prioritize.repository.telemetry.TelemetrySampleValue;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reduces a list of readings to a series — pure computation, no database, so every grouping and
 * aggregation rule is testable without a context. Aggregation runs here rather than in SQL because the
 * slot boundaries are calendar slots in a caller-chosen time zone, which H2 and PostgreSQL would each
 * need their own dialect for.
 *
 * @author peter haller
 */
final class TelemetrySeriesAggregator {

    private TelemetrySeriesAggregator() {
    }

    /**
     * Groups {@code readings} into {@code bucket} slots and reduces each slot with {@code aggregation}.
     *
     * @param readings    the readings of the window, oldest first
     * @param baseline    the newest reading before the window, or {@code null}; only {@link SeriesAggregation#DELTA} uses it
     * @param bucket      the slot size; {@link SeriesBucket#NONE} returns the readings unchanged
     * @param aggregation how a slot is reduced
     * @param zone        the zone the slots are cut in
     * @return one point per non-empty slot, oldest first
     */
    static List<Point> aggregate(List<TelemetrySampleValue> readings, Double baseline,
                                 SeriesBucket bucket, SeriesAggregation aggregation, ZoneId zone) {
        if (bucket == SeriesBucket.NONE) {
            return readings.stream().map(r -> new Point(r.recordedAt(), r.value(), 1)).toList();
        }

        // Readings arrive sorted, so slots are created in chronological order.
        Map<Instant, List<Double>> slots = new LinkedHashMap<>();
        for (TelemetrySampleValue reading : readings) {
            Instant slot = bucket.startOf(reading.recordedAt().atZone(zone)).toInstant();
            slots.computeIfAbsent(slot, k -> new ArrayList<>()).add(reading.value());
        }

        List<Point> points = new ArrayList<>(slots.size());
        Double previousLast = baseline;
        for (Map.Entry<Instant, List<Double>> slot : slots.entrySet()) {
            List<Double> values = slot.getValue();
            double last = values.get(values.size() - 1);
            double value = switch (aggregation) {
                case AVG -> values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
                case MIN -> values.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
                case MAX -> values.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
                case LAST -> last;
                case DELTA -> last - (previousLast != null ? previousLast : values.get(0));
            };
            points.add(new Point(slot.getKey(), value, values.size()));
            previousLast = last;
        }
        return points;
    }
}
