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
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pure unit tests for {@link TelemetrySeriesAggregator} and the number parsing of
 * {@link TelemetrySeriesService} — no Spring context, no database.
 */
class TelemetrySeriesAggregatorTest {

    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    @DisplayName("NONE returns every reading unchanged, one sample each")
    void none_returnsRawReadings() {
        List<Point> points = TelemetrySeriesAggregator.aggregate(
                List.of(reading("2026-10-01T10:00:00Z", 1), reading("2026-10-01T10:00:30Z", 2)),
                null, SeriesBucket.NONE, SeriesAggregation.MAX, UTC);

        assertEquals(List.of(
                new Point(Instant.parse("2026-10-01T10:00:00Z"), 1, 1),
                new Point(Instant.parse("2026-10-01T10:00:30Z"), 2, 1)), points);
    }

    @Test
    @DisplayName("AVG/MIN/MAX/LAST reduce each hour slot; empty slots are omitted")
    void gaugeAggregations_perHour() {
        List<TelemetrySampleValue> readings = List.of(
                reading("2026-10-01T10:05:00Z", 100),
                reading("2026-10-01T10:35:00Z", 300),
                reading("2026-10-01T10:55:00Z", 200),
                reading("2026-10-01T12:10:00Z", -50)); // feed-in: negative power, no reading at 11:xx

        Instant ten = Instant.parse("2026-10-01T10:00:00Z");
        Instant twelve = Instant.parse("2026-10-01T12:00:00Z");
        assertEquals(List.of(new Point(ten, 200, 3), new Point(twelve, -50, 1)),
                TelemetrySeriesAggregator.aggregate(readings, null, SeriesBucket.HOUR, SeriesAggregation.AVG, UTC));
        assertEquals(List.of(new Point(ten, 100, 3), new Point(twelve, -50, 1)),
                TelemetrySeriesAggregator.aggregate(readings, null, SeriesBucket.HOUR, SeriesAggregation.MIN, UTC));
        assertEquals(List.of(new Point(ten, 300, 3), new Point(twelve, -50, 1)),
                TelemetrySeriesAggregator.aggregate(readings, null, SeriesBucket.HOUR, SeriesAggregation.MAX, UTC));
        assertEquals(List.of(new Point(ten, 200, 3), new Point(twelve, -50, 1)),
                TelemetrySeriesAggregator.aggregate(readings, null, SeriesBucket.HOUR, SeriesAggregation.LAST, UTC));
    }

    @Test
    @DisplayName("DELTA measures each slot against the last reading before it, starting from the baseline")
    void delta_usesBaselineThenPreviousSlot() {
        List<TelemetrySampleValue> counter = List.of(
                reading("2026-10-01T10:10:00Z", 15119.5),
                reading("2026-10-01T10:50:00Z", 15120.0),
                reading("2026-10-01T11:20:00Z", 15120.75));

        List<Point> points = TelemetrySeriesAggregator.aggregate(
                counter, 15119.0, SeriesBucket.HOUR, SeriesAggregation.DELTA, UTC);

        assertEquals(1.0, points.get(0).value(), 1e-9, "10:xx = 15120.0 − baseline 15119.0");
        assertEquals(0.75, points.get(1).value(), 1e-9, "11:xx = 15120.75 − 15120.0");
    }

    @Test
    @DisplayName("DELTA without a baseline measures the first slot from its own first reading")
    void delta_withoutBaseline_startsAtFirstReading() {
        List<Point> points = TelemetrySeriesAggregator.aggregate(
                List.of(reading("2026-10-01T10:10:00Z", 10), reading("2026-10-01T10:50:00Z", 12.5)),
                null, SeriesBucket.HOUR, SeriesAggregation.DELTA, UTC);

        assertEquals(2.5, points.get(0).value(), 1e-9);
    }

    @Test
    @DisplayName("DAY slots are local days of the requested zone, not UTC days")
    void day_isCutInTheRequestedZone() {
        // 23:30 UTC on Oct 1 is already 01:30 on Oct 2 in Berlin (CEST, UTC+2)
        List<TelemetrySampleValue> readings = List.of(
                reading("2026-10-01T21:30:00Z", 1),
                reading("2026-10-01T23:30:00Z", 2));

        List<Point> utcDays = TelemetrySeriesAggregator.aggregate(
                readings, null, SeriesBucket.DAY, SeriesAggregation.MAX, UTC);
        List<Point> berlinDays = TelemetrySeriesAggregator.aggregate(
                readings, null, SeriesBucket.DAY, SeriesAggregation.MAX, BERLIN);

        assertEquals(1, utcDays.size());
        assertEquals(2, berlinDays.size());
        assertEquals(Instant.parse("2026-09-30T22:00:00Z"), berlinDays.get(0).at(), "Oct 1, 00:00 CEST");
        assertEquals(Instant.parse("2026-10-01T22:00:00Z"), berlinDays.get(1).at(), "Oct 2, 00:00 CEST");
    }

    @Test
    @DisplayName("WEEK slots start on Monday, MONTH slots on the first")
    void weekAndMonth_slotStarts() {
        List<TelemetrySampleValue> readings = List.of(reading("2026-10-04T12:00:00Z", 1)); // a Sunday

        assertEquals(Instant.parse("2026-09-28T00:00:00Z"),
                TelemetrySeriesAggregator.aggregate(readings, null, SeriesBucket.WEEK, SeriesAggregation.LAST, UTC)
                        .get(0).at());
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"),
                TelemetrySeriesAggregator.aggregate(readings, null, SeriesBucket.MONTH, SeriesAggregation.LAST, UTC)
                        .get(0).at());
    }

    @Test
    @DisplayName("parseNumber accepts finite decimals and rejects text, NaN and infinity")
    void parseNumber() {
        assertEquals(21.5, TelemetrySeriesService.parseNumber(" 21.5 "));
        assertEquals(-377.0, TelemetrySeriesService.parseNumber("-377"));
        assertNull(TelemetrySeriesService.parseNumber("ON"));
        assertNull(TelemetrySeriesService.parseNumber(""));
        assertNull(TelemetrySeriesService.parseNumber(null));
        assertNull(TelemetrySeriesService.parseNumber("NaN"));
        assertNull(TelemetrySeriesService.parseNumber("Infinity"));
    }

    private static TelemetrySampleValue reading(String at, double value) {
        return new TelemetrySampleValue(Instant.parse(at), value);
    }
}
