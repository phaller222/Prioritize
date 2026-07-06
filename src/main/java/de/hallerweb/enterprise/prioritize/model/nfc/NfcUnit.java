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

package de.hallerweb.enterprise.prioritize.model.nfc;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.PAuthorizedObject;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A physical NFC tag mounted on a {@link Resource}. A resource can carry several tags of
 * different {@link NfcUnitType types} (or none at all). Scanning a tag resolves the tag by its
 * {@link #uuid} and triggers a type-specific action.
 * <p>
 * The unit itself is a lightweight trigger; it holds no time-tracking state. For a
 * {@link NfcUnitType#TIMETRACKER} tag the bound {@link #task} is the single task whose tracking
 * a scan toggles &mdash; the tracked time lives on that {@link Task}, not here.
 *
 * @author peter haller
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NfcUnit implements PAuthorizedObject {

    /** Semantics of a tag; determines what a scan does. */
    public enum NfcUnitType {
        COUNTER, CHECKPOINT, TIMETRACKER, INFOPOINT, OTHER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UUID printed on / stored in the physical NFC tag; the key a scan arrives with. */
    @Column(unique = true)
    private String uuid;

    private String name;
    private String description;

    @Enumerated(EnumType.STRING)
    private NfcUnitType type;

    /** Free-form payload written to the tag (e.g. an info text). */
    private String payload;

    /** Monotonically increasing counter, bumped on every scan of a {@link NfcUnitType#COUNTER}. */
    @Builder.Default
    private long sequenceNumber = 0;

    /** When the tag was last scanned; {@code null} until the first scan. */
    private Instant lastScanTime;

    /** The resource this tag is physically mounted on (owning side of the relation). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id")
    @JsonBackReference("resource-nfc")
    private Resource resource;

    /**
     * For a {@link NfcUnitType#TIMETRACKER} tag: the single task whose time tracking a scan
     * toggles. {@code null} for all other types (and for an unbound tracker).
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    @JsonIgnore
    private Task task;

    /** Exposes the bound task's id in JSON without serializing the whole task graph. */
    public Long getBoundTaskId() {
        return task != null ? task.getId() : null;
    }

    @Override
    public Long getId() {
        return this.id;
    }
}
