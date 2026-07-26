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

package de.hallerweb.enterprise.prioritize.service.process;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.hallerweb.enterprise.prioritize.model.nfc.NfcUnit.NfcUnitType;
import de.hallerweb.enterprise.prioritize.model.telemetry.Severity;
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryState;
import de.hallerweb.enterprise.prioritize.service.nfc.NfcScannedEvent;
import de.hallerweb.enterprise.prioritize.service.nfc.NfcUnitService.ScanResult;
import de.hallerweb.enterprise.prioritize.service.telemetry.TelemetryThresholdEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ExecutionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Unit test for the correlation core of {@link EngineEventBridge}: an NFC scan wakes exactly the
 * instances that wait for that resource, and nothing else. The real-engine proof lives in the
 * acceptance test; here the runtime is mocked so the matching rule can be exercised in isolation.
 *
 * @author peter haller
 */
class EngineEventBridgeTest {

    private RuntimeService runtimeService;
    private ExecutionQuery executionQuery;
    private PlatformTransactionManager txManager;
    private EngineEventBridge bridge;

    @BeforeEach
    void setUp() {
        runtimeService = mock(RuntimeService.class);
        executionQuery = mock(ExecutionQuery.class);
        when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
        when(executionQuery.messageEventSubscriptionName(anyString())).thenReturn(executionQuery);
        txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SimpleTransactionStatus());
        bridge = new EngineEventBridge(runtimeService, txManager);
    }

    private static Execution execution(String id) {
        Execution execution = mock(Execution.class);
        when(execution.getId()).thenReturn(id);
        return execution;
    }

    private static NfcScannedEvent scanOf(Long resourceId) {
        ScanResult result = new ScanResult("tag-uuid", NfcUnitType.COUNTER, "COUNTED", null, null, 1L);
        return new NfcScannedEvent(result, resourceId, "scanner-user", Instant.now());
    }

    @Test
    @DisplayName("delivers to the instance whose awaited resource matches the scanned one")
    void deliversToMatchingInstance() {
        Execution waiting = execution("exec-1");
        when(executionQuery.list()).thenReturn(List.of(waiting));
        when(runtimeService.getVariable("exec-1", EngineEventBridge.VAR_AWAITED_RESOURCE_ID)).thenReturn(42L);

        bridge.onNfcScan(scanOf(42L));

        verify(runtimeService).messageEventReceived(eq(EngineEventBridge.MSG_NFC_SCAN), eq("exec-1"), any());
    }

    @Test
    @DisplayName("carries the scan context into the woken instance as process variables")
    void injectsScanContext() {
        Execution waiting = execution("exec-1");
        when(executionQuery.list()).thenReturn(List.of(waiting));
        when(runtimeService.getVariable("exec-1", EngineEventBridge.VAR_AWAITED_RESOURCE_ID)).thenReturn(42L);

        bridge.onNfcScan(scanOf(42L));

        @SuppressWarnings("unchecked")
        var payloadCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(runtimeService).messageEventReceived(anyString(), eq("exec-1"), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("tag-uuid", payload.get(EngineEventBridge.VAR_NFC_SCAN_UUID));
        org.junit.jupiter.api.Assertions.assertEquals("COUNTED", payload.get(EngineEventBridge.VAR_NFC_SCAN_ACTION));
        org.junit.jupiter.api.Assertions.assertEquals(42L, payload.get(EngineEventBridge.VAR_NFC_SCAN_RESOURCE_ID));
        org.junit.jupiter.api.Assertions.assertEquals("scanner-user", payload.get(EngineEventBridge.VAR_NFC_SCAN_BY));
    }

    @Test
    @DisplayName("delivers in its own new transaction (REQUIRES_NEW), not the source event's")
    void deliversInItsOwnNewTransaction() {
        // Regression guard: the bridge fires in the AFTER_COMMIT phase of the scan's transaction, which is
        // still bound to the thread. Only a REQUIRES_NEW transaction gives the woken process a fresh,
        // committing session — with the default REQUIRED its platform writes are silently lost.
        Execution waiting = execution("exec-1");
        when(executionQuery.list()).thenReturn(List.of(waiting));
        when(runtimeService.getVariable("exec-1", EngineEventBridge.VAR_AWAITED_RESOURCE_ID)).thenReturn(42L);

        bridge.onNfcScan(scanOf(42L));

        var defCaptor = org.mockito.ArgumentCaptor.forClass(
                org.springframework.transaction.TransactionDefinition.class);
        verify(txManager).getTransaction(defCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                defCaptor.getValue().getPropagationBehavior(),
                "delivery must run in a brand-new transaction, or the woken process's writes are lost");
    }

    @Test
    @DisplayName("does not deliver to an instance waiting for a different resource")
    void skipsNonMatchingInstance() {
        Execution waiting = execution("exec-1");
        when(executionQuery.list()).thenReturn(List.of(waiting));
        when(runtimeService.getVariable("exec-1", EngineEventBridge.VAR_AWAITED_RESOURCE_ID)).thenReturn(99L);

        bridge.onNfcScan(scanOf(42L));

        verify(runtimeService, never()).messageEventReceived(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("fans out to every instance awaiting the scanned resource")
    void fansOutToAllMatching() {
        Execution one = execution("exec-1");
        Execution two = execution("exec-2");
        Execution other = execution("exec-3");
        when(executionQuery.list()).thenReturn(List.of(one, two, other));
        when(runtimeService.getVariable("exec-1", EngineEventBridge.VAR_AWAITED_RESOURCE_ID)).thenReturn(42L);
        when(runtimeService.getVariable("exec-2", EngineEventBridge.VAR_AWAITED_RESOURCE_ID)).thenReturn(42L);
        when(runtimeService.getVariable("exec-3", EngineEventBridge.VAR_AWAITED_RESOURCE_ID)).thenReturn(7L);

        bridge.onNfcScan(scanOf(42L));

        verify(runtimeService).messageEventReceived(anyString(), eq("exec-1"), any());
        verify(runtimeService).messageEventReceived(anyString(), eq("exec-2"), any());
        verify(runtimeService, never()).messageEventReceived(anyString(), eq("exec-3"), any());
    }

    @Test
    @DisplayName("matches numerically, tolerating a resource id stored as a String")
    void matchesResourceIdStoredAsString() {
        Execution waiting = execution("exec-1");
        when(executionQuery.list()).thenReturn(List.of(waiting));
        when(runtimeService.getVariable("exec-1", EngineEventBridge.VAR_AWAITED_RESOURCE_ID)).thenReturn("42");

        bridge.onNfcScan(scanOf(42L));

        verify(runtimeService).messageEventReceived(anyString(), eq("exec-1"), any());
    }

    @Test
    @DisplayName("one instance failing to receive does not stop delivery to the others")
    void oneFailureDoesNotStopFanOut() {
        Execution failing = execution("exec-1");
        Execution ok = execution("exec-2");
        when(executionQuery.list()).thenReturn(List.of(failing, ok));
        when(runtimeService.getVariable("exec-1", EngineEventBridge.VAR_AWAITED_RESOURCE_ID)).thenReturn(42L);
        when(runtimeService.getVariable("exec-2", EngineEventBridge.VAR_AWAITED_RESOURCE_ID)).thenReturn(42L);
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(runtimeService).messageEventReceived(anyString(), eq("exec-1"), any());

        bridge.onNfcScan(scanOf(42L));

        verify(runtimeService, times(1)).messageEventReceived(anyString(), eq("exec-2"), any());
    }

    @Test
    @DisplayName("a scan not bound to a resource never queries the engine")
    void ignoresResourcelessScan() {
        bridge.onNfcScan(scanOf(null));

        verify(runtimeService, never()).createExecutionQuery();
    }

    @Test
    @DisplayName("a telemetry flank wakes the instance awaiting that resource on the threshold message")
    void telemetryFlankWakesAwaitingInstance() {
        Execution waiting = execution("exec-1");
        when(executionQuery.list()).thenReturn(List.of(waiting));
        when(runtimeService.getVariable("exec-1", EngineEventBridge.VAR_AWAITED_RESOURCE_ID)).thenReturn(42L);

        bridge.onTelemetryThreshold(new TelemetryThresholdEvent(
                7L, 42L, "temperature", 91.5, TelemetryState.ALARM, Severity.WARNING, Instant.now()));

        @SuppressWarnings("unchecked")
        var payloadCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(runtimeService).messageEventReceived(
                eq(EngineEventBridge.MSG_TELEMETRY_THRESHOLD), eq("exec-1"), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("ALARM", payload.get(EngineEventBridge.VAR_TELEMETRY_STATE));
        org.junit.jupiter.api.Assertions.assertEquals("temperature", payload.get(EngineEventBridge.VAR_TELEMETRY_DATAPOINT));
        org.junit.jupiter.api.Assertions.assertEquals(91.5, payload.get(EngineEventBridge.VAR_TELEMETRY_VALUE));
        org.junit.jupiter.api.Assertions.assertEquals(42L, payload.get(EngineEventBridge.VAR_TELEMETRY_RESOURCE_ID));
        org.junit.jupiter.api.Assertions.assertEquals("WARNING", payload.get(EngineEventBridge.VAR_TELEMETRY_SEVERITY));
    }

    @Test
    @DisplayName("a telemetry flank not bound to a resource never queries the engine")
    void ignoresResourcelessThreshold() {
        bridge.onTelemetryThreshold(new TelemetryThresholdEvent(
                7L, null, "temperature", 91.5, TelemetryState.ALARM, Severity.WARNING, Instant.now()));

        verify(runtimeService, never()).createExecutionQuery();
    }
}
