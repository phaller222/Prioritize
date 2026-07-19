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

import de.hallerweb.enterprise.prioritize.model.telemetry.Severity;
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryState;
import java.time.Instant;

/**
 * Wire form of a telemetry threshold transition, published as JSON on the MQTT alarm topic. The
 * {@code type} discriminator mirrors the inbound telemetry convention so subscribers can route by
 * payload type, exactly like {@code NfcScanBroadcast}.
 *
 * @author peter haller
 */
public record TelemetryAlarmBroadcast(String type,
                                      Long ruleId,
                                      Long resourceId,
                                      String datapoint,
                                      double value,
                                      TelemetryState state,
                                      Severity severity,
                                      Instant at) {

    /** Discriminator value carried in {@link #type}. */
    public static final String TYPE = "TELEMETRY_ALARM";

    /** Builds a broadcast from the domain event. */
    public static TelemetryAlarmBroadcast from(TelemetryThresholdEvent event) {
        return new TelemetryAlarmBroadcast(TYPE, event.ruleId(), event.resourceId(),
                event.datapoint(), event.value(), event.newState(), event.severity(), event.at());
    }
}
