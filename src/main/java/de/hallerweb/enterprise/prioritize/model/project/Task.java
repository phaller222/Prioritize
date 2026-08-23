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

package de.hallerweb.enterprise.prioritize.model.project;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.hallerweb.enterprise.prioritize.model.PActor;
import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.calendar.TimeSpan;
import de.hallerweb.enterprise.prioritize.model.document.Document;
import de.hallerweb.enterprise.prioritize.model.project.goal.ProjectGoal;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.skill.SkillRecord;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A single work item on a {@link Blackboard}. A task can be assigned to a {@link PActor} (a
 * person or a machine) and carries the resources, documents and skills needed to complete it.
 * Its lifecycle is tracked via {@link TaskStatus}.
 *
 * @author peter haller
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true) // id-based (via PObject); all-field hashing would walk lazy relations
public class Task extends PObject {

    private int priority;
    private String name;
    private String description;

    @Enumerated(EnumType.STRING)
    private TaskStatus taskStatus;

    /** The actor currently responsible for this task; {@code null} if unassigned. */
    @ManyToOne
    private PActor assignee;

    /**
     * The goal this task contributes to; {@code null} if the task is not tied to any goal.
     * Tasks without a goal do not count towards project progress.
     */
    @ManyToOne
    private ProjectGoal goal;

    /** Resources needed to carry out this task. */
    @JsonIgnore
    @Builder.Default
    @ManyToMany
    private Set<Resource> resources = new HashSet<>();

    /** Documents attached to this task. */
    @JsonIgnore
    @Builder.Default
    @ManyToMany
    private Set<Document> documents = new HashSet<>();

    /** Skills required to carry out this task. */
    @JsonIgnore
    @Builder.Default
    @ManyToMany
    private Set<SkillRecord> requiredSkills = new HashSet<>();

    /** The blackboard owning this task (owning side of the relation). */
    @JsonIgnore
    @ManyToOne
    private Blackboard blackboard;

    /**
     * Id of the BPMN process instance this task belongs to, or {@code null} when no process is
     * involved. Deliberately a plain String and not a relation: the engine owns its instances, this
     * is only a pointer into them, and the task keeps working exactly as before if Flowable is never
     * used. The reverse direction — which task a running instance is about — is carried by the
     * instance's business key and its {@code taskId} variable.
     */
    private String processInstanceId;

    /**
     * Completed time-tracking spans. Each entry is a closed {@link TimeSpan} of type
     * {@link TimeSpan.TimeSpanType#TIME_TRACKER}. Owned by the task and stored via a
     * {@code task_id} column. A {@code List} (not a {@code Set}) on purpose: a transient span's
     * id-based {@code hashCode} would otherwise break removal from a hash-based collection.
     */
    @JsonIgnore
    @Builder.Default
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "task_id")
    private List<TimeSpan> timeSpent = new ArrayList<>();

    /**
     * The currently running time-tracking spans, one per person clocked in; empty while nobody
     * tracks. On stop a span is closed and moved into {@link #timeSpent}.
     * <p>
     * A collection rather than a single span because a crew works the same task at once - one
     * sticker on the site container serves everybody. With a single span the second person to scan
     * would stop the first one's clock, which made the task total itself wrong (two hours booked
     * where four were worked), quite apart from any per-person reporting.
     * <p>
     * A {@code List} for the same reason as {@link #timeSpent}: an id-based {@code hashCode} would
     * break removal from a hash-based collection.
     * <p>
     * Deliberately without {@code orphanRemoval}: stopping moves a span out of here and into
     * {@link #timeSpent}, and orphan removal would read that departure as "delete it" - silently
     * destroying the very session that was just recorded.
     */
    @JsonIgnore
    @Builder.Default
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "active_task_id")
    private List<TimeSpan> activeTimeSpans = new ArrayList<>();

    /** Whether time tracking is currently running for this task, for anybody at all. */
    public boolean isTracking() {
        return !activeTimeSpans.isEmpty();
    }

    /** Whether the given user has a clock running on this task. */
    public boolean isTrackingFor(PUser user) {
        return activeTimeSpanFor(user) != null;
    }

    /** How many people have a clock running on this task right now. */
    public int getRunningCount() {
        return activeTimeSpans.size();
    }

    /**
     * The given user's open span on this task, or {@code null} when they are not clocked in. The
     * owner of a span is the participant recorded on it, so this matches on {@code involvedUsers}.
     */
    public TimeSpan activeTimeSpanFor(PUser user) {
        if (user == null || user.getId() == null) {
            return null;
        }
        return activeTimeSpans.stream()
                .filter(span -> span.getInvolvedUsers().stream()
                        .anyMatch(u -> user.getId().equals(u.getId())))
                .findFirst()
                .orElse(null);
    }
}
