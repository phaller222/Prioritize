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
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryOperator;
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryRule;
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryState;
import de.hallerweb.enterprise.prioritize.repository.telemetry.TelemetryRuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link TelemetryRuleService} — the flank/hysteresis logic and the
 * evaluate-and-fire orchestration. No Spring context, no database.
 *
 * @author peter haller
 */
class TelemetryRuleServiceTest {

    private static TelemetryRule rule(TelemetryOperator op, Double threshold,
                                      Double high, Double hysteresis, TelemetryState state) {
        return TelemetryRule.builder()
                .datapoint("temp")
                .operator(op)
                .threshold(threshold)
                .thresholdHigh(high)
                .hysteresis(hysteresis)
                .severity(Severity.WARNING)
                .enabled(true)
                .state(state)
                .build();
    }

    // ---- nextState: flank logic --------------------------------------------------------------

    @Test
    @DisplayName("GT: OK bleibt OK unter Schwelle, kippt auf ALARM darüber")
    void gt_okToAlarmOnBreach() {
        TelemetryRule r = rule(TelemetryOperator.GT, 30.0, null, 0.0, TelemetryState.OK);
        assertSame(TelemetryState.OK, TelemetryRuleService.nextState(r, 29.9));
        assertSame(TelemetryState.OK, TelemetryRuleService.nextState(r, 30.0)); // not strictly greater
        assertSame(TelemetryState.ALARM, TelemetryRuleService.nextState(r, 30.1));
    }

    @Test
    @DisplayName("GT mit Hysterese: ALARM klärt erst unter Schwelle minus Dead-Band")
    void gt_hysteresisHoldsAlarmInDeadBand() {
        TelemetryRule r = rule(TelemetryOperator.GT, 30.0, null, 2.0, TelemetryState.ALARM);
        assertSame(TelemetryState.ALARM, TelemetryRuleService.nextState(r, 29.0)); // still in dead-band
        assertSame(TelemetryState.ALARM, TelemetryRuleService.nextState(r, 28.1));
        assertSame(TelemetryState.OK, TelemetryRuleService.nextState(r, 28.0)); // cleared t-h
    }

    @Test
    @DisplayName("LT: kippt auf ALARM unter Schwelle, klärt über Schwelle plus Dead-Band")
    void lt_flankWithHysteresis() {
        TelemetryRule ok = rule(TelemetryOperator.LT, 10.0, null, 1.0, TelemetryState.OK);
        assertSame(TelemetryState.ALARM, TelemetryRuleService.nextState(ok, 9.9));
        assertSame(TelemetryState.OK, TelemetryRuleService.nextState(ok, 10.0));

        TelemetryRule alarm = rule(TelemetryOperator.LT, 10.0, null, 1.0, TelemetryState.ALARM);
        assertSame(TelemetryState.ALARM, TelemetryRuleService.nextState(alarm, 10.5)); // dead-band
        assertSame(TelemetryState.OK, TelemetryRuleService.nextState(alarm, 11.0)); // cleared t+h
    }

    @Test
    @DisplayName("RANGE: ALARM außerhalb des Bandes, OK wieder innerhalb (mit Dead-Band)")
    void range_outsideBandAlarms() {
        TelemetryRule ok = rule(TelemetryOperator.RANGE, 10.0, 20.0, 1.0, TelemetryState.OK);
        assertSame(TelemetryState.OK, TelemetryRuleService.nextState(ok, 15.0));
        assertSame(TelemetryState.ALARM, TelemetryRuleService.nextState(ok, 20.1));
        assertSame(TelemetryState.ALARM, TelemetryRuleService.nextState(ok, 9.9));

        TelemetryRule alarm = rule(TelemetryOperator.RANGE, 10.0, 20.0, 1.0, TelemetryState.ALARM);
        assertSame(TelemetryState.ALARM, TelemetryRuleService.nextState(alarm, 20.5)); // still in band-edge dead zone
        assertSame(TelemetryState.OK, TelemetryRuleService.nextState(alarm, 19.0)); // within [t+h, high-h]
    }

    // ---- evaluate: orchestration -------------------------------------------------------------

    @Test
    @DisplayName("evaluate: bei Flanke wird Regel-State persistiert und Event publiziert")
    void evaluate_firesEventAndPersistsOnFlank() {
        TelemetryRuleRepository repo = mock(TelemetryRuleRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        TelemetryRule r = rule(TelemetryOperator.GT, 30.0, null, 0.0, TelemetryState.OK);
        r.setId(5L);
        when(repo.findByResource_IdAndDatapointAndEnabledTrue(7L, "temp")).thenReturn(List.of(r));

        new TelemetryRuleService(repo, publisher).evaluate(7L, "temp", "35.0");

        assertSame(TelemetryState.ALARM, r.getState());
        verify(repo).save(r);
        ArgumentCaptor<TelemetryThresholdEvent> captor =
                ArgumentCaptor.forClass(TelemetryThresholdEvent.class);
        verify(publisher).publishEvent(captor.capture());
        TelemetryThresholdEvent ev = captor.getValue();
        assertEquals(5L, ev.ruleId());
        assertEquals(7L, ev.resourceId());
        assertEquals("temp", ev.datapoint());
        assertEquals(35.0, ev.value());
        assertSame(TelemetryState.ALARM, ev.newState());
        assertSame(Severity.WARNING, ev.severity());
    }

    @Test
    @DisplayName("evaluate: kein Flankenwechsel -> weder speichern noch Event")
    void evaluate_noTransitionNoSideEffects() {
        TelemetryRuleRepository repo = mock(TelemetryRuleRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        TelemetryRule r = rule(TelemetryOperator.GT, 30.0, null, 0.0, TelemetryState.OK);
        when(repo.findByResource_IdAndDatapointAndEnabledTrue(eq(7L), eq("temp")))
                .thenReturn(List.of(r));

        new TelemetryRuleService(repo, publisher).evaluate(7L, "temp", "20.0");

        assertSame(TelemetryState.OK, r.getState());
        verify(repo, never()).save(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("evaluate: nicht-numerischer Wert wird ignoriert (kein Repo-Zugriff)")
    void evaluate_nonNumericIgnored() {
        TelemetryRuleRepository repo = mock(TelemetryRuleRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        new TelemetryRuleService(repo, publisher).evaluate(7L, "temp", "OFFLINE");

        verify(repo, never()).findByResource_IdAndDatapointAndEnabledTrue(any(), any());
        verify(publisher, never()).publishEvent(any());
    }
}
