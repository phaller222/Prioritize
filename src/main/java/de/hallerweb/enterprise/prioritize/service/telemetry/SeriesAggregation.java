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

/**
 * How the readings of one {@link SeriesBucket} slot are reduced to a single value.
 * <ul>
 *   <li>{@link #AVG}, {@link #MIN}, {@link #MAX} — for gauges such as a temperature or a momentary
 *       power draw.</li>
 *   <li>{@link #LAST} — the reading at the end of the slot, e.g. a meter's counter reading.</li>
 *   <li>{@link #DELTA} — how far a counter advanced in the slot: its last reading minus the last
 *       reading before the slot (the newest reading before the window for the first slot; when there
 *       is none, the slot's own first reading). Turns a cumulative meter reading into consumption per
 *       slot. Empty slots are skipped, so after a gap the whole advance lands in the next slot that has
 *       readings; a counter that was reset (meter swap) shows up as a negative delta.</li>
 * </ul>
 *
 * @author peter haller
 */
public enum SeriesAggregation {
    AVG, MIN, MAX, LAST, DELTA
}
