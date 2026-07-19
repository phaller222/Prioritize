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

import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryOperator;
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryRule;
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryState;
import de.hallerweb.enterprise.prioritize.repository.telemetry.TelemetryRuleRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Evaluates {@link TelemetryRule}s on the telemetry ingest path and fires a
 * {@link TelemetryThresholdEvent} whenever a rule changes state. Called from
 * {@code ResourceService} right after a value has been appended and the resource saved, so it runs
 * inside the same transaction; the event is therefore delivered {@code AFTER_COMMIT} to listeners.
 * <p>
 * The core is {@link #nextState(TelemetryRule, double)}, a pure function of the current state and
 * the new value: a breach is decided against the raw threshold, but an existing alarm only clears
 * once the value has moved back past the threshold by the rule's hysteresis margin. This flank
 * logic — fire on transition only, dead-band on the way back — is the whole point of the primitive
 * and is unit-tested in isolation.
 *
 * @author peter haller
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class TelemetryRuleService {

    private final TelemetryRuleRepository ruleRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Evaluates every active rule watching {@code datapoint} of the given resource against the just
     * ingested {@code rawValue}. Rules whose state flips are persisted and get a
     * {@link TelemetryThresholdEvent} published. Non-numeric values are ignored (a data point may
     * legitimately carry text), as are resources/data points without rules.
     *
     * @param resourceId the resource the value was recorded for
     * @param datapoint  the data point name
     * @param rawValue   the ingested value, as received (may be non-numeric)
     */
    public void evaluate(Long resourceId, String datapoint, String rawValue) {
        double value;
        try {
            value = Double.parseDouble(rawValue.trim());
        } catch (NumberFormatException | NullPointerException ex) {
            return; // non-numeric telemetry is not something a threshold rule can act on
        }

        List<TelemetryRule> rules =
                ruleRepository.findByResource_IdAndDatapointAndEnabledTrue(resourceId, datapoint);
        for (TelemetryRule rule : rules) {
            TelemetryState next = nextState(rule, value);
            if (next != rule.getState()) {
                rule.setState(next);
                rule.setLastTransitionAt(LocalDateTime.now());
                ruleRepository.save(rule);
                eventPublisher.publishEvent(new TelemetryThresholdEvent(
                        rule.getId(), resourceId, datapoint, value,
                        next, rule.getSeverity(), Instant.now()));
                log.debug("Telemetry rule {} on resource {} datapoint '{}' -> {} (value={})",
                        rule.getId(), resourceId, datapoint, next, value);
            }
        }
    }

    /**
     * Pure flank logic: given a rule's current state and a new value, the state it should be in.
     * When currently OK it goes to ALARM on a raw-threshold breach; when currently in ALARM it
     * returns to OK only once the value clears the threshold by the hysteresis dead-band. Returning
     * the same state means no transition (and thus no event).
     */
    static TelemetryState nextState(TelemetryRule rule, double value) {
        if (rule.getState() == TelemetryState.ALARM) {
            return cleared(rule, value) ? TelemetryState.OK : TelemetryState.ALARM;
        }
        return breached(rule, value) ? TelemetryState.ALARM : TelemetryState.OK;
    }

    /** Whether {@code value} breaches the rule's raw threshold(s). */
    private static boolean breached(TelemetryRule rule, double value) {
        double t = rule.getThreshold();
        return switch (rule.getOperator()) {
            case GT -> value > t;
            case LT -> value < t;
            case RANGE -> value < t || value > rule.getThresholdHigh();
        };
    }

    /** Whether {@code value} has cleared the threshold by the hysteresis margin (dead-band). */
    private static boolean cleared(TelemetryRule rule, double value) {
        double h = rule.getHysteresis() == null ? 0.0 : rule.getHysteresis();
        double t = rule.getThreshold();
        return switch (rule.getOperator()) {
            case GT -> value <= t - h;
            case LT -> value >= t + h;
            case RANGE -> value >= t + h && value <= rule.getThresholdHigh() - h;
        };
    }
}
