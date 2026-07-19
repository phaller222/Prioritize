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

package de.hallerweb.enterprise.prioritize.model.telemetry;

/**
 * Comparison a {@link TelemetryRule} applies to an incoming numeric data point to decide whether it
 * is in breach.
 * <ul>
 *   <li>{@link #GT} — breach when {@code value > threshold} (e.g. over-temperature).</li>
 *   <li>{@link #LT} — breach when {@code value < threshold} (e.g. under-voltage).</li>
 *   <li>{@link #RANGE} — an acceptable band {@code [threshold, thresholdHigh]}; breach when the
 *       value falls outside it.</li>
 * </ul>
 * A rate-of-change (DELTA) operator is intentionally not part of this first cut; it needs the
 * previous reading and its timestamp and is deferred to a follow-up slice.
 *
 * @author peter haller
 */
public enum TelemetryOperator {
    GT,
    LT,
    RANGE
}
