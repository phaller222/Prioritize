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

import de.hallerweb.enterprise.prioritize.model.resource.NameValueEntry;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryRule;
import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryState;
import de.hallerweb.enterprise.prioritize.repository.telemetry.TelemetryRuleRepository;
import de.hallerweb.enterprise.prioritize.service.resource.ResourceService;
import de.hallerweb.enterprise.prioritize.service.security.SystemIdentityProvider;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read side of the platform for a BPMN diagram: a Spring bean named {@code platformState} through
 * which a gateway condition asks about a resource's live state, so a process can <em>branch</em> on the
 * real world without a service task and without knowing any repository.
 * <pre>{@code
 * <sequenceFlow sourceRef="check" targetRef="react">
 *   <conditionExpression xsi:type="tFormalExpression">
 *     ${platformState.telemetryState(resourceId, 'temperature') == 'ALARM'}
 *   </conditionExpression>
 * </sequenceFlow>
 * }</pre>
 * <p>
 * <b>The read twin of the inbound facade.</b> Where {@link PlatformGateway} lets a process <em>act</em>
 * on the platform, this lets it <em>read</em> — the same principle applies: every read runs under the
 * {@link SystemIdentityProvider system identity} and is gated by that principal's resource
 * {@code READ} permission, so what a diagram may see is a checked property of the platform's own
 * identity, not an open back door.
 * <p>
 * <b>Reading, not deciding.</b> The bean answers questions of fact (is it online, is it in alarm, what
 * was the last value); the branching logic itself stays in the diagram, and any real computation stays
 * in Java. This is the line the whole Flowable work holds: BPMN describes order, waiting and
 * responsibility, it does not compute.
 *
 * @author peter haller
 */
@Service
public class PlatformState {

    private final ResourceService resourceService;
    private final TelemetryRuleRepository telemetryRuleRepository;
    private final SystemIdentityProvider systemIdentity;

    public PlatformState(ResourceService resourceService,
                         TelemetryRuleRepository telemetryRuleRepository,
                         SystemIdentityProvider systemIdentity) {
        this.resourceService = resourceService;
        this.telemetryRuleRepository = telemetryRuleRepository;
        this.systemIdentity = systemIdentity;
    }

    /**
     * Whether a resource is currently online. Only MQTT resources report presence; a resource that does
     * not is reported offline.
     *
     * @param resourceId the resource to query
     * @return {@code true} if the resource is an online MQTT resource
     * @throws NoSuchElementException if no resource has that id
     * @throws org.springframework.security.access.AccessDeniedException if the system principal may not read it
     */
    @Transactional(readOnly = true)
    public boolean resourceOnline(Long resourceId) {
        Resource resource = resourceService.getResource(resourceId, system());
        return Boolean.TRUE.equals(resource.getMqttOnline());
    }

    /**
     * The aggregate telemetry state of a resource's data point: {@code "ALARM"} if any enabled rule on
     * that data point is currently in alarm, otherwise {@code "OK"}. A data point with no enabled rule
     * is not in alarm, so it reads {@code "OK"} — the absence of a rule is deliberately not a third
     * state, which keeps a gateway condition a simple {@code == 'ALARM'} comparison.
     *
     * @param resourceId the resource to query
     * @param datapoint  the data point name
     * @return {@code "OK"} or {@code "ALARM"}
     * @throws NoSuchElementException if no resource has that id
     * @throws org.springframework.security.access.AccessDeniedException if the system principal may not read it
     */
    @Transactional(readOnly = true)
    public String telemetryState(Long resourceId, String datapoint) {
        resourceService.getResource(resourceId, system()); // READ gate, same identity as every other read here
        boolean anyAlarm = telemetryRuleRepository
                .findByResource_IdAndDatapointAndEnabledTrue(resourceId, datapoint).stream()
                .anyMatch(rule -> rule.getState() == TelemetryState.ALARM);
        return (anyAlarm ? TelemetryState.ALARM : TelemetryState.OK).name();
    }

    /**
     * The most recent numeric value reported for a resource's data point.
     * <p>
     * This is loud on purpose: a gateway that reads a data point which never reported, or whose last
     * value is not a number, is a misconfigured diagram, and failing the expression makes that visible
     * at once rather than letting a silent default drive the branch the wrong way.
     *
     * @param resourceId the resource to query
     * @param datapoint  the data point name
     * @return the last value as a double
     * @throws NoSuchElementException if no resource has that id, or the data point has no numeric value yet
     * @throws org.springframework.security.access.AccessDeniedException if the system principal may not read it
     */
    @Transactional(readOnly = true)
    public double latestValue(Long resourceId, String datapoint) {
        Resource resource = resourceService.getResource(resourceId, system());
        String history = resource.getMqttValues() == null ? null : resource.getMqttValues().stream()
                .filter(entry -> datapoint.equals(entry.getMqttName()))
                .map(NameValueEntry::getMqttValues)
                .findFirst()
                .orElse(null);
        if (history == null || history.isBlank()) {
            throw new NoSuchElementException("Resource " + resourceId + " has no value for data point '"
                    + datapoint + "'.");
        }
        String[] values = history.split(",");
        String latest = values[values.length - 1].trim();
        try {
            return Double.parseDouble(latest);
        } catch (NumberFormatException notANumber) {
            throw new NoSuchElementException("Latest value '" + latest + "' of resource " + resourceId
                    + " data point '" + datapoint + "' is not numeric.");
        }
    }

    private PUser system() {
        return systemIdentity.get();
    }
}
