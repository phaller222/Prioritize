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

import de.hallerweb.enterprise.prioritize.model.resource.NameValueEntry;

/**
 * The most recent reading of a single telemetry data point of a resource. The stored value is a
 * comma-separated, capped history (see {@code ResourceService.MAX_VALUE_HISTORY}); this DTO exposes only
 * the last (newest) entry, which is what a dashboard renders. The read side that the MQTT/REST ingest
 * ({@code POST /resources/{id}/values}) never offered until now.
 *
 * @param name  the data point name (e.g. {@code "temperature"})
 * @param value the newest recorded value, or {@code null} if the history is empty
 */
public record ResourceValueDTO(String name, String value) {

    /**
     * Maps a {@link NameValueEntry} to its latest-value view, taking the last element of the
     * comma-separated history. Returns a {@code null} value for an empty/blank history.
     */
    public static ResourceValueDTO from(NameValueEntry entry) {
        return new ResourceValueDTO(entry.getMqttName(), latestOf(entry.getMqttValues()));
    }

    /** Returns the last comma-separated token of {@code history}, or {@code null} when there is none. */
    private static String latestOf(String history) {
        if (history == null || history.isBlank()) {
            return null;
        }
        String[] values = history.split(",");
        return values[values.length - 1];
    }
}
