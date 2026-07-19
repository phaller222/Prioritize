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
 * Severity a {@link TelemetryRule} carries. It is propagated verbatim on the
 * {@code TelemetryThresholdEvent} so downstream actions (alarm broadcast, task creation, …) can
 * route or prioritize by it. Kept deliberately generic — the platform makes no assumption about how
 * a consumer maps these to its own notification levels.
 *
 * @author peter haller
 */
public enum Severity {
    INFO,
    WARNING,
    CRITICAL
}
