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

package de.hallerweb.enterprise.prioritize.model.process;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A BPMN process definition known to the platform, carried by a {@link DocumentInfo} of the documents
 * subsystem. The document is the <b>leading history</b>: a human edits the diagram there and gets
 * versioning, permissions, groups and project assignment for free. This entity adds only what the
 * documents subsystem must not know about — the engine-facing lifecycle.
 * <p>
 * <b>Satellite, never the other way round:</b> a definition points at its document; a document has no
 * collection of definitions. Same shape as
 * {@link de.hallerweb.enterprise.prioritize.model.scheduling.TaskSchedule} → project and telemetry
 * rules → resource, and it keeps Flowable semantics out of the documents subsystem entirely.
 * <p>
 * <b>Two histories exist and that is intended.</b> Flowable versions every deployment of the same
 * process key on its own, and running instances stay on the definition they were started with. The
 * document revision is the source, the Flowable deployment the derived runtime form; {@link
 * #deploymentId} together with {@link #deployedVersion} maps between them in both directions.
 *
 * @author peter haller
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true) // id-based (via PObject); all-field hashing would walk the lazy document
public class ProcessDefinition extends PObject {

    /**
     * The BPMN process id ({@code <bpmn:process id="…">}), unique across the platform. Read out of the
     * document content on registration, never supplied by the caller: the file is the truth.
     * <p>
     * The unique constraint is only the second line of defence — {@code ddl-auto: update} does not
     * retrofit it onto an existing table, so the service checks for a collision explicitly.
     */
    @Column(unique = true)
    private String processKey;

    /** Human-readable name, taken from the BPMN process name or the document name. */
    private String name;

    /** The document carrying the BPMN source. Its current version is what activation deploys. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private DocumentInfo documentInfo;

    /** Where this definition stands between registration and the engine. */
    @Enumerated(EnumType.STRING)
    private ProcessDefinitionState state;

    // ---- engine link (all null until the first activation) -------------------------------------

    /** Id of the Flowable deployment created by the last activation. */
    private String deploymentId;

    /** The {@link de.hallerweb.enterprise.prioritize.model.document.Document#getVersion() document version} that was deployed. */
    private Integer deployedVersion;

    /** When the definition was last activated. Audit only. */
    private LocalDateTime deployedAt;

    /** Who last activated it. Audit only. */
    @ManyToOne(fetch = FetchType.EAGER)
    private PUser deployedBy;
}
