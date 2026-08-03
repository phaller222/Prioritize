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

package de.hallerweb.enterprise.prioritize.dto.resource;

import de.hallerweb.enterprise.prioritize.dto.telemetry.TelemetryRuleDTO;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;

import java.util.List;

/**
 * One resource together with everything a status view needs about it: its base data, the newest
 * reading of each telemetry data point, and its monitoring rules with their current OK/ALARM state.
 * <p>
 * It exists to collapse a fan-out. The same picture can be assembled from
 * {@code GET /resources} plus {@code GET /resources/{id}/values/latest} plus
 * {@code GET /resources/{id}/telemetry-rules}, but that costs <em>1 + 2N</em> HTTP calls for N
 * resources — and each one re-authenticates and re-resolves the calling user. A polling client pays
 * that price on every tick. The three parts stay separate records rather than being flattened, so the
 * generated clients keep reusing {@link ResourceDTO}, {@link ResourceValueDTO} and
 * {@link TelemetryRuleDTO} instead of gaining a fourth, near-duplicate model.
 *
 * @author peter haller
 */
public record ResourceStatusDTO(ResourceDTO resource,
                                List<ResourceValueDTO> latestValues,
                                List<TelemetryRuleDTO> telemetryRules) {

    /**
     * Assembles the view for one resource. The rules are passed in rather than looked up here: the
     * caller fetches them for all resources at once, which is the whole point of this endpoint.
     *
     * @param resource the resource, already authorized and with its occupancy set
     * @param rules    its monitoring rules, may be empty
     */
    public static ResourceStatusDTO from(Resource resource, List<TelemetryRuleDTO> rules) {
        List<ResourceValueDTO> values = resource.getMqttValues() == null
                ? List.of()
                : resource.getMqttValues().stream().sorted().map(ResourceValueDTO::from).toList();

        return new ResourceStatusDTO(
                ResourceDTO.from(resource),
                values,
                rules == null ? List.of() : rules);
    }
}
