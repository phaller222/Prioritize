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
@EqualsAndHashCode(callSuper = true)
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
