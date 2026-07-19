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
 * Domain event raised by {@code ResourceService} when a {@code TelemetryRule} changes state, i.e. on
 * the flank OK&rarr;ALARM or ALARM&rarr;OK. It carries just enough context for a transport-agnostic
 * reaction (which rule/resource/data point, the triggering value, the new state and severity).
 * <p>
 * Mirrors the {@code NfcScannedEvent} pattern: the service stays free of any transport dependency,
 * and listeners should observe it {@code AFTER_COMMIT} so only persisted transitions propagate
 * (e.g. {@link TelemetryAlarmMqttBridge} broadcasts it over MQTT).
 *
 * @author peter haller
 */
public record TelemetryThresholdEvent(Long ruleId,
                                      Long resourceId,
                                      String datapoint,
                                      double value,
                                      TelemetryState newState,
                                      Severity severity,
                                      Instant at) {
}
