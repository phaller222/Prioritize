package de.hallerweb.enterprise.prioritize.model.project;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.hallerweb.enterprise.prioritize.model.PActor;
import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.document.DocumentInfo;
import de.hallerweb.enterprise.prioritize.model.project.goal.ProjectGoal;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.model.skill.SkillGroup;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A project groups a team of {@link PUser members}, assigned {@link Resource resources} and
 * {@link DocumentInfo documents} around a shared goal, managed by a single {@link PActor manager}.
 * Its work items live on the associated {@link Blackboard} as {@link Task tasks}.
 * <p>
 * Access is membership-based: only the manager or a member may read the project, and only the
 * manager may modify it (enforced in the service layer, not via the role/permission system).
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
public class Project extends PObject {

    private String name;
    private String description;
    private int priority;
    private LocalDate beginDate;
    private LocalDate dueDate;

    /** Maximum number of man-days this project is expected to consume. */
    private int maxManDays;

    /** The actor (person or machine) responsible for this project. */
    @ManyToOne
    private PActor manager;

    /** Users participating in this project. */
    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER)
    private Set<PUser> members = new HashSet<>();

    /** Resources assigned to this project. */
    @JsonIgnore
    @Builder.Default
    @ManyToMany
    private Set<Resource> resources = new HashSet<>();

    /** Documents assigned to this project. */
    @JsonIgnore
    @Builder.Default
    @ManyToMany
    private Set<DocumentInfo> documents = new HashSet<>();

    /** Skills required to carry out this project. */
    @JsonIgnore
    @Builder.Default
    @ManyToMany
    private Set<SkillGroup> requiredSkills = new HashSet<>();

    /** The blackboard holding this project's tasks. Created together with the project. */
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    private Blackboard blackboard;

    /**
     * Target goals pursued by this project. Owned by the project (unidirectional; the
     * {@code project_id} foreign key lives on the goal table). A project's progress is derived
     * from these goals and the tasks assigned to them.
     */
    @JsonIgnore
    @Builder.Default
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "project_id")
    private List<ProjectGoal> goals = new ArrayList<>();
}
