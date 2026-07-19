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

import de.hallerweb.enterprise.prioritize.repository.telemetry.TelemetryRuleRepository;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * In-memory guard that lets the telemetry ingest path skip the per-value rule lookup for resources
 * that have no enabled rule at all — the overwhelmingly common case. It holds the set of resource
 * ids with at least one enabled {@code TelemetryRule}, seeded once at startup and refreshed by the
 * rule CRUD path (the single place rules are mutated). {@code evaluate} consults
 * {@link #mightHaveRules(Long)} before touching the database.
 * <p>
 * The filter is deliberately coarse (resource-level, not per data point): a false positive only
 * costs the query that would have run anyway, while the true negative — a resource with zero rules —
 * is eliminated entirely. It is a per-instance cache; in a (currently non-existent) multi-instance
 * deployment a rule change would need to be broadcast to peers, so refreshes are kept behind this
 * one seam for a later cache-invalidation hook.
 *
 * @author peter haller
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class TelemetryRuleGuard {

    private final TelemetryRuleRepository ruleRepository;
    private final Set<Long> resourcesWithEnabledRules = ConcurrentHashMap.newKeySet();

    /** Seeds the cache once the context is up (a query needs the JPA layer ready). */
    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        resourcesWithEnabledRules.addAll(ruleRepository.findResourceIdsWithEnabledRules());
        log.debug("Telemetry guard seeded with {} resource(s) having enabled rules.",
                resourcesWithEnabledRules.size());
    }

    /**
     * Whether the given resource might have an enabled rule and therefore warrants a rule lookup.
     * Cheap in-memory check on the ingest hot path.
     */
    public boolean mightHaveRules(Long resourceId) {
        return resourcesWithEnabledRules.contains(resourceId);
    }

    /**
     * Recomputes membership for one resource after its rules changed (create/update/delete). Adds it
     * when it now has an enabled rule, drops it otherwise.
     */
    public void refresh(Long resourceId) {
        if (ruleRepository.existsByResource_IdAndEnabledTrue(resourceId)) {
            resourcesWithEnabledRules.add(resourceId);
        } else {
            resourcesWithEnabledRules.remove(resourceId);
        }
    }
}
