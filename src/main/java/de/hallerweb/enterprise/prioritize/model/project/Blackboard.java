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
import de.hallerweb.enterprise.prioritize.model.PObject;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A blackboard holds the {@link Task tasks} of a single {@link Project}. When {@code frozen},
 * the task list is meant to be locked against structural changes.
 *
 * @author peter haller
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true) // id-based (via PObject); avoids the Blackboard<->Project cycle
public class Blackboard extends PObject {

    private String title;
    private String description;
    private boolean frozen;

    /** Tasks on this blackboard. The owning side is {@link Task#getBlackboard()}. */
    @Builder.Default
    @OneToMany(mappedBy = "blackboard", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Task> tasks = new ArrayList<>();

    /** The project this blackboard belongs to (inverse side). */
    @JsonIgnore
    @OneToOne(mappedBy = "blackboard")
    private Project project;

    /**
     * Adds a task to this blackboard and keeps the bidirectional link consistent.
     *
     * @param task the task to add
     */
    public void addTask(Task task) {
        tasks.add(task);
        task.setBlackboard(this);
    }

    /**
     * Removes a task from this blackboard and clears its back-reference.
     *
     * @param task the task to remove
     */
    public void removeTask(Task task) {
        tasks.remove(task);
        task.setBlackboard(null);
    }
}
