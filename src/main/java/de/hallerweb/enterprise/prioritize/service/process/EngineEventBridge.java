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

import de.hallerweb.enterprise.prioritize.service.nfc.NfcScannedEvent;
import de.hallerweb.enterprise.prioritize.service.nfc.NfcUnitService.ScanResult;
import de.hallerweb.enterprise.prioritize.service.telemetry.TelemetryThresholdEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.Execution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The outbound bridge: the one place where a domain event reaches into a running BPMN process. It turns
 * {@link NfcScannedEvent} and {@link TelemetryThresholdEvent} into a Flowable message so a process that
 * was <em>waiting</em> for that real-world signal can continue.
 * <p>
 * <b>Orchestration, not ownership</b> (the line held throughout the Flowable work): the bridge only
 * wakes a process that already chose to wait. Nothing here starts a process, owns a lifecycle, or
 * changes platform state — remove Flowable and the events simply have one listener fewer.
 * <p>
 * <b>One correlation rule.</b> An event carries a {@code resourceId}; a waiting instance states which
 * resource it waits for in a process variable named {@value #VAR_AWAITED_RESOURCE_ID}. Correlation is
 * exactly the pair <em>(message name, awaited resource id)</em> — deliberately <b>not</b> the business
 * key, which already answers "what is this instance about" ({@code project:}/{@code task:}) and must not
 * be overloaded. A BPMN author writes:
 * <pre>{@code
 * <intermediateCatchEvent id="awaitScan">
 *   <messageEventDefinition messageRef="nfcScan"/>
 * </intermediateCatchEvent>
 * }</pre>
 * with {@code nfcScan} declared as a {@code <message name="nfcScan"/>}, and sets
 * {@code awaitedResourceId} to the resource being watched (e.g. from a start variable). The resource id
 * is compared <b>numerically</b>, so it does not matter whether the author typed it as a {@code Long},
 * an {@code Integer} or a numeric {@code String} — the single most common way this kind of correlation
 * silently fails.
 * <p>
 * <b>Fan-out to all matching instances.</b> A resource may legitimately feed several processes at once
 * (an inspection round and an alarm escalation, say), so the message is delivered to every waiting
 * execution whose awaited resource matches, not just one.
 * <p>
 * <b>After commit, in a fresh transaction, best effort.</b> Like the MQTT bridges, delivery is handled
 * {@link TransactionPhase#AFTER_COMMIT AFTER_COMMIT} so only persisted signals propagate, and a
 * delivery failure is logged, never propagated back to the already-committed source operation. Each
 * {@code messageEventReceived} is wrapped in its own Spring transaction: the woken process runs on
 * synchronously and may reach back into the platform through the facade (create a task, store a
 * document), and those JPA writes must share one committing transaction with the engine's own — without
 * it the process advances but its platform effects are silently lost (the AFTER_COMMIT phase has no
 * active transaction of its own). One transaction per instance also keeps the failure isolation: one
 * instance rolling back does not drag the others down.
 *
 * @author peter haller
 */
@Component
public class EngineEventBridge {

    private static final Logger log = LoggerFactory.getLogger(EngineEventBridge.class);

    /** Message name a process waits on to be woken by an NFC scan of its awaited resource. */
    public static final String MSG_NFC_SCAN = "nfcScan";

    /** Message name a process waits on to be woken by a telemetry OK&harr;ALARM flank of its resource. */
    public static final String MSG_TELEMETRY_THRESHOLD = "telemetryThreshold";

    /**
     * Process variable through which a waiting instance names the resource it waits for. The one
     * correlation axis for every outbound event, compared numerically against the event's resource id.
     */
    public static final String VAR_AWAITED_RESOURCE_ID = "awaitedResourceId";

    /** Payload variable: the scanned tag's uuid. */
    public static final String VAR_NFC_SCAN_UUID = "nfcScanUuid";
    /** Payload variable: the scan action ({@code TRACKING_STARTED}, {@code COUNTED}, …). */
    public static final String VAR_NFC_SCAN_ACTION = "nfcScanAction";
    /** Payload variable: the resource the scanned tag sits on. */
    public static final String VAR_NFC_SCAN_RESOURCE_ID = "nfcScanResourceId";
    /** Payload variable: who scanned the tag. */
    public static final String VAR_NFC_SCAN_BY = "nfcScanBy";

    /** Payload variable: the new telemetry state, {@code OK} or {@code ALARM}. */
    public static final String VAR_TELEMETRY_STATE = "telemetryState";
    /** Payload variable: the resource whose rule changed state. */
    public static final String VAR_TELEMETRY_RESOURCE_ID = "telemetryResourceId";
    /** Payload variable: the data point that crossed the threshold. */
    public static final String VAR_TELEMETRY_DATAPOINT = "telemetryDatapoint";
    /** Payload variable: the value that triggered the transition. */
    public static final String VAR_TELEMETRY_VALUE = "telemetryValue";
    /** Payload variable: the transition's severity. */
    public static final String VAR_TELEMETRY_SEVERITY = "telemetrySeverity";

    private final RuntimeService runtimeService;
    private final TransactionTemplate transactionTemplate;

    public EngineEventBridge(RuntimeService runtimeService, PlatformTransactionManager transactionManager) {
        this.runtimeService = runtimeService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // REQUIRES_NEW, not the default REQUIRED: we run in the AFTER_COMMIT phase of the source event's
        // transaction, which is still bound to the thread while it completes. REQUIRED would join that
        // already-committed transaction, and the woken process's platform writes would never be flushed.
        // A brand-new transaction with its own session is what makes the engine and JPA state commit together.
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Wakes every process instance that waits (on {@value #MSG_NFC_SCAN}) for the resource this tag sits
     * on, injecting the scan's context as process variables so the diagram can react to it. A scan that
     * nobody waits for is a normal no-op.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNfcScan(NfcScannedEvent event) {
        if (event.resourceId() == null) {
            return; // a scan not bound to a resource cannot correlate to a waiting instance
        }
        ScanResult result = event.result();
        Map<String, Object> payload = new HashMap<>();
        payload.put(VAR_NFC_SCAN_UUID, result != null ? result.uuid() : null);
        payload.put(VAR_NFC_SCAN_ACTION, result != null ? result.action() : null);
        payload.put(VAR_NFC_SCAN_RESOURCE_ID, event.resourceId());
        payload.put(VAR_NFC_SCAN_BY, event.scannedBy());

        deliver(MSG_NFC_SCAN, event.resourceId(), payload);
    }

    /**
     * Wakes every process instance that waits (on {@value #MSG_TELEMETRY_THRESHOLD}) for the resource
     * whose telemetry rule just changed state, injecting the transition (new state, value, data point,
     * severity) so the diagram can branch on it. Fired only on the OK&harr;ALARM flank, so a woken
     * process reacts to a real change, not to every reading.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTelemetryThreshold(TelemetryThresholdEvent event) {
        if (event.resourceId() == null) {
            return; // a transition not bound to a resource cannot correlate to a waiting instance
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put(VAR_TELEMETRY_STATE, event.newState() != null ? event.newState().name() : null);
        payload.put(VAR_TELEMETRY_RESOURCE_ID, event.resourceId());
        payload.put(VAR_TELEMETRY_DATAPOINT, event.datapoint());
        payload.put(VAR_TELEMETRY_VALUE, event.value());
        payload.put(VAR_TELEMETRY_SEVERITY, event.severity() != null ? event.severity().name() : null);

        deliver(MSG_TELEMETRY_THRESHOLD, event.resourceId(), payload);
    }

    /**
     * Delivers a message to every instance waiting on {@code messageName} for {@code resourceId}, each in
     * its own new transaction so the woken process's platform effects commit together with the engine's
     * state (see the class note). One instance failing does not stop the others.
     */
    private void deliver(String messageName, Long resourceId, Map<String, Object> payload) {
        List<String> targets = matchingExecutionIds(messageName, resourceId);
        int delivered = 0;
        for (String executionId : targets) {
            try {
                transactionTemplate.executeWithoutResult(status ->
                        runtimeService.messageEventReceived(messageName, executionId, payload));
                delivered++;
            } catch (RuntimeException ex) {
                // One instance failing to receive must not stop the others, nor bubble back to the
                // already-committed source event.
                log.warn("Delivering '{}' for resource {} to execution {} failed: {}",
                        messageName, resourceId, executionId, ex.getMessage());
            }
        }
        if (delivered > 0) {
            log.info("Outbound '{}' for resource {} delivered to {} waiting instance(s).",
                    messageName, resourceId, delivered);
        } else {
            log.debug("Outbound '{}' for resource {}: no instance was waiting.", messageName, resourceId);
        }
    }

    /**
     * The ids of the executions waiting on {@code messageName} for {@code resourceId} — the correlation
     * itself, separate from delivery. The candidate set (executions holding a subscription with that name)
     * is small, and each is checked against the awaited resource with a numeric compare. Package-visible
     * so the correlation can be verified against the real engine without going through the per-instance
     * transaction wrapper, which the unit test and the live smoke cover instead.
     */
    List<String> matchingExecutionIds(String messageName, Long resourceId) {
        List<String> ids = new ArrayList<>();
        for (Execution execution : runtimeService.createExecutionQuery()
                .messageEventSubscriptionName(messageName).list()) {
            Object awaited = runtimeService.getVariable(execution.getId(), VAR_AWAITED_RESOURCE_ID);
            if (matchesResource(awaited, resourceId)) {
                ids.add(execution.getId());
            }
        }
        return ids;
    }

    /**
     * Whether an instance's awaited-resource variable names the given resource. Compared numerically so
     * the convention tolerates the resource id being stored as a {@link Number} or a numeric
     * {@link String} — the seam where correlation most often fails silently.
     */
    private static boolean matchesResource(Object awaited, Long resourceId) {
        if (awaited == null || resourceId == null) {
            return false;
        }
        if (awaited instanceof Number number) {
            return number.longValue() == resourceId;
        }
        try {
            return Long.parseLong(awaited.toString().trim()) == resourceId;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }
}
