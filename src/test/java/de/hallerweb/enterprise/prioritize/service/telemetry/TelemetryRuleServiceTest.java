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

import de.hallerweb.enterprise.prioritize.dto.telemetry.TelemetryRuleDTO;
import de.hallerweb.enterprise.prioritize.dto.telemetry.TelemetryRuleRequest;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.Action;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.telemetry.Severity;
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryOperator;
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryRule;
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryState;
import de.hallerweb.enterprise.prioritize.repository.resource.ResourceRepository;
import de.hallerweb.enterprise.prioritize.repository.telemetry.TelemetryRuleRepository;
import de.hallerweb.enterprise.prioritize.service.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link TelemetryRuleService} — the flank/hysteresis logic, the
 * evaluate-and-fire orchestration (incl. the guard skip) and the CRUD/authorization behavior. No
 * Spring context, no database.
 *
 * @author peter haller
 */
class TelemetryRuleServiceTest {

    private TelemetryRuleRepository ruleRepository;
    private ResourceRepository resourceRepository;
    private AuthorizationService authService;
    private TelemetryRuleGuard guard;
    private ApplicationEventPublisher publisher;
    private TelemetryRuleService service;

    private final PUser user = new PUser();

    @BeforeEach
    void setUp() {
        ruleRepository = mock(TelemetryRuleRepository.class);
        resourceRepository = mock(ResourceRepository.class);
        authService = mock(AuthorizationService.class);
        guard = mock(TelemetryRuleGuard.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new TelemetryRuleService(
                ruleRepository, resourceRepository, authService, guard, publisher);
    }

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

    private Resource resourceWithId(long id) {
        Resource resource = mock(Resource.class);
        when(resource.getId()).thenReturn(id);
        return resource;
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
        assertSame(TelemetryState.ALARM, TelemetryRuleService.nextState(alarm, 20.5)); // band-edge dead zone
        assertSame(TelemetryState.OK, TelemetryRuleService.nextState(alarm, 19.0)); // within [t+h, high-h]
    }

    // ---- evaluate: orchestration -------------------------------------------------------------

    @Test
    @DisplayName("evaluate: bei Flanke wird Regel-State persistiert und Event publiziert")
    void evaluate_firesEventAndPersistsOnFlank() {
        TelemetryRule r = rule(TelemetryOperator.GT, 30.0, null, 0.0, TelemetryState.OK);
        r.setId(5L);
        when(guard.mightHaveRules(7L)).thenReturn(true);
        when(ruleRepository.findByResource_IdAndDatapointAndEnabledTrue(7L, "temp"))
                .thenReturn(List.of(r));

        service.evaluate(7L, "temp", "35.0");

        assertSame(TelemetryState.ALARM, r.getState());
        verify(ruleRepository).save(r);
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
        TelemetryRule r = rule(TelemetryOperator.GT, 30.0, null, 0.0, TelemetryState.OK);
        when(guard.mightHaveRules(7L)).thenReturn(true);
        when(ruleRepository.findByResource_IdAndDatapointAndEnabledTrue(eq(7L), eq("temp")))
                .thenReturn(List.of(r));

        service.evaluate(7L, "temp", "20.0");

        assertSame(TelemetryState.OK, r.getState());
        verify(ruleRepository, never()).save(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("evaluate: Guard meldet keine Regeln -> kein Query, kein Event")
    void evaluate_guardShortCircuits() {
        when(guard.mightHaveRules(7L)).thenReturn(false);

        service.evaluate(7L, "temp", "35.0");

        verify(ruleRepository, never()).findByResource_IdAndDatapointAndEnabledTrue(any(), any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("evaluate: nicht-numerischer Wert wird ignoriert (kein Query)")
    void evaluate_nonNumericIgnored() {
        when(guard.mightHaveRules(7L)).thenReturn(true);

        service.evaluate(7L, "temp", "OFFLINE");

        verify(ruleRepository, never()).findByResource_IdAndDatapointAndEnabledTrue(any(), any());
        verify(publisher, never()).publishEvent(any());
    }

    // ---- CRUD ---------------------------------------------------------------------------------

    @Test
    @DisplayName("createRule: gültige Anfrage speichert, aktualisiert Guard und liefert DTO")
    void createRule_persistsAndRefreshesGuard() {
        Resource resource = resourceWithId(7L);
        when(resourceRepository.findById(7L)).thenReturn(Optional.of(resource));
        when(authService.hasPermission(user, resource, Action.UPDATE)).thenReturn(true);
        when(ruleRepository.save(any())).thenAnswer(inv -> {
            TelemetryRule r = inv.getArgument(0);
            r.setId(11L);
            return r;
        });

        TelemetryRuleRequest req = new TelemetryRuleRequest(
                "temp", TelemetryOperator.GT, 30.0, null, 2.0, Severity.CRITICAL, null);
        TelemetryRuleDTO dto = service.createRule(7L, req, user);

        assertEquals(11L, dto.id());
        assertEquals(7L, dto.resourceId());
        assertEquals("temp", dto.datapoint());
        assertSame(TelemetryOperator.GT, dto.operator());
        assertSame(Severity.CRITICAL, dto.severity());
        assertTrue(dto.enabled(), "enabled should default to true when omitted");
        assertSame(TelemetryState.OK, dto.state());
        verify(ruleRepository).save(any(TelemetryRule.class));
        verify(guard).refresh(7L);
    }

    @Test
    @DisplayName("createRule: fehlendes datapoint -> 400, nichts gespeichert")
    void createRule_missingDatapoint_throws() {
        Resource resource = resourceWithId(7L);
        when(resourceRepository.findById(7L)).thenReturn(Optional.of(resource));
        when(authService.hasPermission(user, resource, Action.UPDATE)).thenReturn(true);

        TelemetryRuleRequest req = new TelemetryRuleRequest(
                " ", TelemetryOperator.GT, 30.0, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.createRule(7L, req, user));
        verify(ruleRepository, never()).save(any());
        verify(guard, never()).refresh(any());
    }

    @Test
    @DisplayName("createRule: RANGE ohne thresholdHigh -> 400")
    void createRule_rangeWithoutHigh_throws() {
        Resource resource = resourceWithId(7L);
        when(resourceRepository.findById(7L)).thenReturn(Optional.of(resource));
        when(authService.hasPermission(user, resource, Action.UPDATE)).thenReturn(true);

        TelemetryRuleRequest req = new TelemetryRuleRequest(
                "temp", TelemetryOperator.RANGE, 10.0, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.createRule(7L, req, user));
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRule: unbekannte Resource -> NoSuchElementException")
    void createRule_resourceNotFound_throws() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        TelemetryRuleRequest req = new TelemetryRuleRequest(
                "temp", TelemetryOperator.GT, 30.0, null, null, null, null);
        assertThrows(NoSuchElementException.class, () -> service.createRule(99L, req, user));
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("createRule: ohne UPDATE-Recht -> AccessDeniedException, nichts gespeichert")
    void createRule_noPermission_throws() {
        Resource resource = resourceWithId(7L);
        when(resourceRepository.findById(7L)).thenReturn(Optional.of(resource));
        when(authService.hasPermission(user, resource, Action.UPDATE)).thenReturn(false);

        TelemetryRuleRequest req = new TelemetryRuleRequest(
                "temp", TelemetryOperator.GT, 30.0, null, null, null, null);
        assertThrows(AccessDeniedException.class, () -> service.createRule(7L, req, user));
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("getRules: READ-Recht vorausgesetzt, mappt Regeln auf DTOs")
    void getRules_mapsDtos() {
        Resource resource = resourceWithId(7L);
        when(resourceRepository.findById(7L)).thenReturn(Optional.of(resource));
        when(authService.hasPermission(user, resource, Action.READ)).thenReturn(true);
        TelemetryRule r = rule(TelemetryOperator.LT, 5.0, null, null, TelemetryState.OK);
        r.setId(3L);
        r.setResource(resource);
        when(ruleRepository.findByResource_Id(7L)).thenReturn(List.of(r));

        List<TelemetryRuleDTO> dtos = service.getRules(7L, user);

        assertEquals(1, dtos.size());
        assertEquals(3L, dtos.get(0).id());
        assertEquals(7L, dtos.get(0).resourceId());
    }

    @Test
    @DisplayName("updateRule: enabled-Toggle wird angewandt, gespeichert und Guard aktualisiert")
    void updateRule_appliesPatchAndRefreshesGuard() {
        Resource resource = resourceWithId(7L);
        TelemetryRule r = rule(TelemetryOperator.GT, 30.0, null, 2.0, TelemetryState.ALARM);
        r.setId(4L);
        r.setResource(resource);
        when(ruleRepository.findById(4L)).thenReturn(Optional.of(r));
        when(authService.hasPermission(user, resource, Action.UPDATE)).thenReturn(true);
        when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TelemetryRuleRequest patch = new TelemetryRuleRequest(
                null, null, 32.0, null, null, null, false);
        TelemetryRuleDTO dto = service.updateRule(4L, patch, user);

        assertEquals(32.0, dto.threshold());
        assertTrue(!dto.enabled(), "enabled should be toggled off");
        assertSame(TelemetryState.ALARM, dto.state(), "state must not be reset by an edit");
        verify(ruleRepository).save(r);
        verify(guard).refresh(7L);
    }

    @Test
    @DisplayName("deleteRule: löscht und aktualisiert Guard")
    void deleteRule_deletesAndRefreshesGuard() {
        Resource resource = resourceWithId(7L);
        TelemetryRule r = rule(TelemetryOperator.GT, 30.0, null, null, TelemetryState.OK);
        r.setId(4L);
        r.setResource(resource);
        when(ruleRepository.findById(4L)).thenReturn(Optional.of(r));
        when(authService.hasPermission(user, resource, Action.UPDATE)).thenReturn(true);

        service.deleteRule(4L, user);

        verify(ruleRepository).delete(r);
        verify(guard).refresh(7L);
    }

    @Test
    @DisplayName("deleteRule: ohne UPDATE-Recht -> AccessDeniedException, nichts gelöscht")
    void deleteRule_noPermission_throws() {
        Resource resource = resourceWithId(7L);
        TelemetryRule r = rule(TelemetryOperator.GT, 30.0, null, null, TelemetryState.OK);
        r.setResource(resource);
        when(ruleRepository.findById(4L)).thenReturn(Optional.of(r));
        when(authService.hasPermission(user, resource, Action.UPDATE)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.deleteRule(4L, user));
        verify(ruleRepository, never()).delete(any());
        verify(guard, never()).refresh(any());
    }
}
