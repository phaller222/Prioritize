package de.hallerweb.enterprise.prioritize.model.project.goal;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A {@link ProjectGoalProperty} requiring a document carrying a given {@code tag}.
 *
 * @author peter haller
 */
@Entity
@DiscriminatorValue("document")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProjectGoalPropertyDocument extends ProjectGoalProperty {

    /** Target tag a document should carry to satisfy this property. */
    private String tag;
}
