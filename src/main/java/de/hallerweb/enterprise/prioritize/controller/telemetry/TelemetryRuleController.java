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

package de.hallerweb.enterprise.prioritize.controller.telemetry;

import de.hallerweb.enterprise.prioritize.config.CurrentUserResolver;
import de.hallerweb.enterprise.prioritize.dto.telemetry.TelemetryRuleDTO;
import de.hallerweb.enterprise.prioritize.dto.telemetry.TelemetryRuleRequest;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.telemetry.TelemetryRuleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for telemetry monitoring rules ({@link TelemetryRuleService}). Rules belong to a resource
 * (they watch one of its data points), so creation and listing are nested under the resource; a rule
 * is then addressed by its own id. Authorization is enforced in the service against the owning
 * resource (READ to read, UPDATE to mutate); {@code GlobalExceptionHandler} maps the thrown
 * exceptions to status codes (404/403/400).
 *
 * @author peter haller
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TelemetryRuleController {

    private final TelemetryRuleService telemetryRuleService;
    private final CurrentUserResolver currentUserResolver;

    private PUser getCurrentUser(Authentication auth) {
        return currentUserResolver.resolve(auth);
    }

    /**
     * Creates a monitoring rule on a resource.
     *
     * @return 201 Created with the new rule
     */
    @PostMapping("/resources/{resourceId}/telemetry-rules")
    public ResponseEntity<TelemetryRuleDTO> createRule(
        @PathVariable Long resourceId,
        @RequestBody TelemetryRuleRequest request,
        Authentication auth) {

        TelemetryRuleDTO created =
                telemetryRuleService.createRule(resourceId, request, getCurrentUser(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Lists all monitoring rules of a resource (any data point, enabled or not).
     *
     * @return 200 OK with the rules
     */
    @GetMapping("/resources/{resourceId}/telemetry-rules")
    public ResponseEntity<List<TelemetryRuleDTO>> getRules(
        @PathVariable Long resourceId,
        Authentication auth) {

        return ResponseEntity.ok(telemetryRuleService.getRules(resourceId, getCurrentUser(auth)));
    }

    /**
     * Returns a single rule by id.
     *
     * @return 200 OK with the rule
     */
    @GetMapping("/telemetry-rules/{id}")
    public ResponseEntity<TelemetryRuleDTO> getRule(
        @PathVariable Long id,
        Authentication auth) {

        return ResponseEntity.ok(telemetryRuleService.getRule(id, getCurrentUser(auth)));
    }

    /**
     * Partially updates a rule (only the fields present in the body are changed).
     *
     * @return 200 OK with the updated rule
     */
    @PatchMapping("/telemetry-rules/{id}")
    public ResponseEntity<TelemetryRuleDTO> updateRule(
        @PathVariable Long id,
        @RequestBody TelemetryRuleRequest patch,
        Authentication auth) {

        return ResponseEntity.ok(telemetryRuleService.updateRule(id, patch, getCurrentUser(auth)));
    }

    /**
     * Deletes a rule.
     *
     * @return 204 No Content
     */
    @DeleteMapping("/telemetry-rules/{id}")
    public ResponseEntity<Void> deleteRule(
        @PathVariable Long id,
        Authentication auth) {

        telemetryRuleService.deleteRule(id, getCurrentUser(auth));
        return ResponseEntity.noContent().build();
    }
}
