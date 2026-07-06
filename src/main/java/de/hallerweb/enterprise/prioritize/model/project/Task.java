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
     * The currently running time-tracking span, or {@code null} when tracking is idle. On stop the
     * span is closed and moved into {@link #timeSpent}.
     */
    @JsonIgnore
    @OneToOne(cascade = CascadeType.ALL)
    private TimeSpan activeTimeSpan;

    /** Whether time tracking is currently running for this task. */
    public boolean isTracking() {
        return activeTimeSpan != null;
    }
}
