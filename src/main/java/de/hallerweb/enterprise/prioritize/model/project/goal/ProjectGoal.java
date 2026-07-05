package de.hallerweb.enterprise.prioritize.model.project.goal;

import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.project.Project;
import de.hallerweb.enterprise.prioritize.model.project.Task;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A target a {@link Project} pursues. Goals are descriptive: they carry a name, a description and
 * an optional set of {@link ProjectGoalProperty properties} specifying what the goal is about.
 * <p>
 * Progress is <em>not</em> stored on the goal; it is derived from the {@link Task tasks} assigned
 * to it (see the project progress service): a goal's completion is the share of its non-cancelled
 * tasks that have reached a terminal-done status.
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
public class ProjectGoal extends PObject {

    private String name;
    private String description;

    /**
     * Descriptive properties owned by this goal. Created and removed together with the goal
     * (unidirectional; the {@code goal_id} foreign key lives on the property table).
     */
    @Builder.Default
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "goal_id")
    private List<ProjectGoalProperty> properties = new ArrayList<>();
}
