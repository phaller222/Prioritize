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
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates {@link TelemetryRule}s on the telemetry ingest path and administers them (CRUD).
 * <p>
 * <b>Evaluation:</b> {@link #evaluate} is called from {@code ResourceService} right after a value
 * has been appended and the resource saved, so it runs inside the same transaction and the event is
 * delivered {@code AFTER_COMMIT} to listeners. Its core is {@link #nextState(TelemetryRule, double)},
 * a pure function of the current state and the new value: a breach is decided against the raw
 * threshold, but an existing alarm only clears once the value has moved back past the threshold by
 * the rule's hysteresis margin. It fires a {@link TelemetryThresholdEvent} only on that flank.
 * <p>
 * <b>Administration:</b> create/read/update/delete over the resource's monitoring rules. Rule
 * management is a per-resource concern, so each operation is authorized against the owning resource
 * ({@link Action#READ} to read, {@link Action#UPDATE} to mutate) — never a global-admin bypass, in
 * line with the rest of the resource API. Every mutation refreshes the {@link TelemetryRuleGuard} so
 * the ingest hot path can keep skipping rule-less resources.
 *
 * @author peter haller
 */
@Service
@RequiredArgsConstructor
@Transactional
@Log4j2
public class TelemetryRuleService {

    private final TelemetryRuleRepository ruleRepository;
    private final ResourceRepository resourceRepository;
    private final AuthorizationService authService;
    private final TelemetryRuleGuard guard;
    private final ApplicationEventPublisher eventPublisher;

    // ---- ingest-path evaluation --------------------------------------------------------------

    /**
     * Evaluates every active rule watching {@code datapoint} of the given resource against the just
     * ingested {@code rawValue}. Rules whose state flips are persisted and get a
     * {@link TelemetryThresholdEvent} published. Resources without any enabled rule are skipped
     * before any query (via {@link TelemetryRuleGuard}); non-numeric values are ignored (a data
     * point may legitimately carry text).
     *
     * @param resourceId the resource the value was recorded for
     * @param datapoint  the data point name
     * @param rawValue   the ingested value, as received (may be non-numeric)
     */
    public void evaluate(Long resourceId, String datapoint, String rawValue) {
        if (!guard.mightHaveRules(resourceId)) {
            return; // no enabled rule on this resource — skip the lookup entirely
        }
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

    // ---- administration (CRUD) ---------------------------------------------------------------

    /**
     * Creates a rule on a resource. Requires {@link Action#UPDATE} on the resource.
     *
     * @throws NoSuchElementException   if the resource does not exist
     * @throws AccessDeniedException    if the user may not update the resource
     * @throws IllegalArgumentException if the request is incomplete or inconsistent
     */
    public TelemetryRuleDTO createRule(Long resourceId, TelemetryRuleRequest req, PUser user) {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new NoSuchElementException("Resource not found"));
        requireUpdate(user, resource);

        String datapoint = req == null ? null : req.datapoint();
        if (datapoint == null || datapoint.isBlank()) {
            throw new IllegalArgumentException("datapoint is required.");
        }
        if (req.operator() == null) {
            throw new IllegalArgumentException("operator is required.");
        }
        if (req.threshold() == null) {
            throw new IllegalArgumentException("threshold is required.");
        }
        validateConsistency(req.operator(), req.threshold(), req.thresholdHigh(), req.hysteresis());

        TelemetryRule rule = TelemetryRule.builder()
                .resource(resource)
                .datapoint(datapoint.trim())
                .operator(req.operator())
                .threshold(req.threshold())
                .thresholdHigh(req.thresholdHigh())
                .hysteresis(req.hysteresis())
                .severity(req.severity() == null ? Severity.WARNING : req.severity())
                .enabled(req.enabled() == null || req.enabled())
                .state(TelemetryState.OK)
                .build();
        TelemetryRule saved = ruleRepository.save(rule);
        guard.refresh(resourceId);
        log.debug("Telemetry rule {} created on resource {} (datapoint '{}').",
                saved.getId(), resourceId, datapoint);
        return TelemetryRuleDTO.from(saved);
    }

    /** Lists a resource's rules (any data point, enabled or not). Requires {@link Action#READ}. */
    @Transactional(readOnly = true)
    public List<TelemetryRuleDTO> getRules(Long resourceId, PUser user) {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new NoSuchElementException("Resource not found"));
        requireRead(user, resource);
        return ruleRepository.findByResource_Id(resourceId).stream()
                .map(TelemetryRuleDTO::from)
                .toList();
    }

    /** Reads a single rule. Requires {@link Action#READ} on its resource. */
    @Transactional(readOnly = true)
    public TelemetryRuleDTO getRule(Long id, PUser user) {
        TelemetryRule rule = requireRule(id);
        requireRead(user, rule.getResource());
        return TelemetryRuleDTO.from(rule);
    }

    /**
     * Applies a partial update (only non-null request fields). Requires {@link Action#UPDATE} on the
     * rule's resource. The persisted alarm {@code state} is not touched — the next reading
     * re-evaluates against the new definition and emits a flank if warranted.
     */
    public TelemetryRuleDTO updateRule(Long id, TelemetryRuleRequest patch, PUser user) {
        TelemetryRule rule = requireRule(id);
        requireUpdate(user, rule.getResource());
        if (patch == null) {
            return TelemetryRuleDTO.from(rule);
        }

        if (patch.datapoint() != null) {
            if (patch.datapoint().isBlank()) {
                throw new IllegalArgumentException("datapoint must not be blank.");
            }
            rule.setDatapoint(patch.datapoint().trim());
        }
        if (patch.operator() != null) {
            rule.setOperator(patch.operator());
        }
        if (patch.threshold() != null) {
            rule.setThreshold(patch.threshold());
        }
        if (patch.thresholdHigh() != null) {
            rule.setThresholdHigh(patch.thresholdHigh());
        }
        if (patch.hysteresis() != null) {
            rule.setHysteresis(patch.hysteresis());
        }
        if (patch.severity() != null) {
            rule.setSeverity(patch.severity());
        }
        if (patch.enabled() != null) {
            rule.setEnabled(patch.enabled());
        }
        // Validate the effective (post-patch) definition, not just the changed fields.
        validateConsistency(rule.getOperator(), rule.getThreshold(),
                rule.getThresholdHigh(), rule.getHysteresis());

        TelemetryRule saved = ruleRepository.save(rule);
        guard.refresh(saved.getResource().getId());
        return TelemetryRuleDTO.from(saved);
    }

    /** Deletes a rule. Requires {@link Action#UPDATE} on its resource. */
    public void deleteRule(Long id, PUser user) {
        TelemetryRule rule = requireRule(id);
        Resource resource = rule.getResource();
        requireUpdate(user, resource);
        ruleRepository.delete(rule);
        if (resource != null) {
            guard.refresh(resource.getId());
        }
    }

    // ---- helpers -----------------------------------------------------------------------------

    private TelemetryRule requireRule(Long id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Telemetry rule not found"));
    }

    private void requireRead(PUser user, Resource resource) {
        if (!authService.hasPermission(user, resource, Action.READ)) {
            throw new AccessDeniedException("No read permission for this resource.");
        }
    }

    private void requireUpdate(PUser user, Resource resource) {
        if (!authService.hasPermission(user, resource, Action.UPDATE)) {
            throw new AccessDeniedException("No update permission for this resource.");
        }
    }

    /** A RANGE rule needs an upper bound strictly above the lower; hysteresis must be non-negative. */
    private static void validateConsistency(TelemetryOperator operator, Double threshold,
                                            Double thresholdHigh, Double hysteresis) {
        if (operator == TelemetryOperator.RANGE) {
            if (thresholdHigh == null) {
                throw new IllegalArgumentException("thresholdHigh is required for a RANGE rule.");
            }
            if (threshold != null && thresholdHigh <= threshold) {
                throw new IllegalArgumentException("thresholdHigh must be greater than threshold.");
            }
        }
        if (hysteresis != null && hysteresis < 0) {
            throw new IllegalArgumentException("hysteresis must not be negative.");
        }
    }
}
