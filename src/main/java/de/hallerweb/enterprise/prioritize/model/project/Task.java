package de.hallerweb.enterprise.prioritize.model.project;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.hallerweb.enterprise.prioritize.model.PActor;
import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.document.Document;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.skill.SkillRecord;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
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
@EqualsAndHashCode(callSuper = true)
public class Task extends PObject {

    private int priority;
    private String name;
    private String description;

    @Enumerated(EnumType.STRING)
    private TaskStatus taskStatus;

    /** The actor currently responsible for this task; {@code null} if unassigned. */
    @ManyToOne
    private PActor assignee;

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
}
