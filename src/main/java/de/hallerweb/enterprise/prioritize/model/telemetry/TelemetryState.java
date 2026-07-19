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
 * Persisted evaluation state of a {@link TelemetryRule}. A rule fires an event only when this state
 * changes (the flank OK&rarr;ALARM or ALARM&rarr;OK), never on every reading that happens to be over
 * threshold — that flank detection is what keeps the stream of downstream actions quiet.
 *
 * @author peter haller
 */
public enum TelemetryState {
    OK,
    ALARM
}
