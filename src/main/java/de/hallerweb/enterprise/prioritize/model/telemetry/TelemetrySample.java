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

package de.hallerweb.enterprise.prioritize.model.telemetry;

import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * One timestamped numeric reading of a telemetry data point — a single row of a measurement series.
 * <p>
 * The comma-separated history on {@code NameValueEntry} keeps only the last 100 values and no time,
 * which is enough for "what is it now" but not for a chart over hours or days. Every numeric reading
 * that arrives through the ingest (MQTT or REST) is therefore also stored here, stamped with the time
 * the platform <em>received</em> it. Device clocks are deliberately not trusted: Tasmota, for one,
 * reports local time without an offset.
 * <p>
 * Rows are written once and never updated. They disappear with their resource (database-level
 * {@code ON DELETE CASCADE}, so no delete path has to remember them) or through the optional retention
 * purge.
 *
 * @author peter haller
 */
@Entity
@Table(indexes = {
        @Index(name = "idx_telemetry_sample_series", columnList = "resource_id, datapoint, recorded_at"),
        @Index(name = "idx_telemetry_sample_recorded_at", columnList = "recorded_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
@ToString(onlyExplicitlyIncluded = true)
public class TelemetrySample extends PObject {

    /** Resource that reported the reading. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Resource resource;

    /** Name of the data point (matches {@code NameValueEntry.mqttName}, e.g. {@code temp}). */
    @ToString.Include
    @Column(nullable = false)
    private String datapoint;

    /** When the platform received the reading. */
    @ToString.Include
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    /** The reading itself; only numeric readings are stored. */
    @ToString.Include
    @Column(name = "numeric_value", nullable = false)
    private double numericValue;
}
