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

package de.hallerweb.enterprise.prioritize.repository.telemetry;

import de.hallerweb.enterprise.prioritize.model.telemetry.TelemetryRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository for {@link TelemetryRule}s.
 *
 * @author peter haller
 */
public interface TelemetryRuleRepository extends JpaRepository<TelemetryRule, Long> {

    /**
     * The active rules watching a given data point of a given resource — the exact set the ingest
     * path evaluates on every incoming value.
     */
    List<TelemetryRule> findByResource_IdAndDatapointAndEnabledTrue(Long resourceId, String datapoint);

    /** All rules of a resource (any data point, enabled or not); used by admin/inspection paths. */
    List<TelemetryRule> findByResource_Id(Long resourceId);

    /** Whether a resource has at least one enabled rule; backs the ingest-path guard's refresh. */
    boolean existsByResource_IdAndEnabledTrue(Long resourceId);

    /** The ids of all resources with at least one enabled rule; seeds the guard at startup. */
    @Query("select distinct r.resource.id from TelemetryRule r where r.enabled = true")
    List<Long> findResourceIdsWithEnabledRules();
}
