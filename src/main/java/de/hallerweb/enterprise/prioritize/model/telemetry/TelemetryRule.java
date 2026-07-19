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

import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A monitoring rule that turns a single telemetry data point of a {@link Resource} from passive
 * history into active alarms. When a value is ingested (MQTT or REST) the rule is evaluated in
 * {@code ResourceService}; it fires a {@code TelemetryThresholdEvent} only on the flank, i.e. when
 * its {@link #state} actually changes (see {@link TelemetryState}).
 * <p>
 * The rule is a neutral platform primitive: it decides <em>that</em> a threshold was crossed and at
 * which {@link Severity}, but the reaction is left to event listeners (an MQTT alarm broadcast
 * ships in this slice; task creation, workflow start, inbox messages are follow-ups).
 * <p>
 * {@link #hysteresis} is a dead-band applied only on the clearing flank to suppress chatter around
 * the threshold: an ALARM does not clear until the value has moved back past the threshold by this
 * margin. {@code 0} (or {@code null}) disables it.
 *
 * @author peter haller
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
@ToString(onlyExplicitlyIncluded = true)
public class TelemetryRule extends PObject {

    /** Resource whose telemetry this rule watches. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Resource resource;

    /** Name of the watched data point (matches {@code NameValueEntry.mqttName}, e.g. {@code temp}). */
    @ToString.Include
    private String datapoint;

    /** Comparison applied to the incoming value. */
    @Enumerated(EnumType.STRING)
    @ToString.Include
    private TelemetryOperator operator;

    /** Primary threshold; for {@link TelemetryOperator#RANGE} the lower bound of the acceptable band. */
    @ToString.Include
    private Double threshold;

    /** Upper bound of the acceptable band; only used by {@link TelemetryOperator#RANGE}. */
    private Double thresholdHigh;

    /** Dead-band applied on the clearing flank to avoid chatter; {@code null}/{@code 0} disables it. */
    private Double hysteresis;

    /** Severity carried on the fired event. */
    @Enumerated(EnumType.STRING)
    private Severity severity;

    /** Whether this rule is evaluated at all. */
    private boolean enabled;

    /** Persisted evaluation state; the rule fires only when this changes. */
    @Enumerated(EnumType.STRING)
    @ToString.Include
    @Builder.Default
    private TelemetryState state = TelemetryState.OK;

    /** When {@link #state} last changed. */
    private LocalDateTime lastTransitionAt;
}
