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
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryRule;
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryState;
import java.time.LocalDateTime;

/**
 * Flat, transport-safe view of a {@link TelemetryRule}. Carries only the owning resource's id, never
 * the lazy {@code Resource} itself, so serializing a rule never triggers a
 * {@code LazyInitializationException} or drags the whole resource graph onto the wire. Mapped inside
 * a service transaction.
 *
 * @author peter haller
 */
public record TelemetryRuleDTO(Long id,
                               Long resourceId,
                               String datapoint,
                               TelemetryOperator operator,
                               Double threshold,
                               Double thresholdHigh,
                               Double hysteresis,
                               Severity severity,
                               boolean enabled,
                               TelemetryState state,
                               LocalDateTime lastTransitionAt) {

    /** Maps an entity to its DTO. Call within a transaction ({@code resource.getId()} is lazy). */
    public static TelemetryRuleDTO from(TelemetryRule rule) {
        return new TelemetryRuleDTO(
                rule.getId(),
                rule.getResource() != null ? rule.getResource().getId() : null,
                rule.getDatapoint(),
                rule.getOperator(),
                rule.getThreshold(),
                rule.getThresholdHigh(),
                rule.getHysteresis(),
                rule.getSeverity(),
                rule.isEnabled(),
                rule.getState(),
                rule.getLastTransitionAt());
    }
}
