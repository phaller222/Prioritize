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

import de.hallerweb.enterprise.prioritize.model.telemetry.Severity;
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryOperator;

/**
 * Request body for creating or patching a {@link de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryRule}.
 * The owning resource comes from the path, never the body. All fields are boxed/nullable so the same
 * record serves a PATCH (only non-null fields are applied); {@code createRule} enforces the ones it
 * requires. {@code state}/{@code lastTransitionAt} are runtime-managed and deliberately absent — a
 * client cannot set a rule's alarm state.
 *
 * @author peter haller
 */
public record TelemetryRuleRequest(String datapoint,
                                   TelemetryOperator operator,
                                   Double threshold,
                                   Double thresholdHigh,
                                   Double hysteresis,
                                   Severity severity,
                                   Boolean enabled) {
}
