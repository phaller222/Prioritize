package de.hallerweb.enterprise.prioritize.model.project.goal;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A {@link ProjectGoalProperty} describing a numeric target range (e.g. a value expected between
 * {@code min} and {@code max}).
 *
 * @author peter haller
 */
@Entity
@DiscriminatorValue("numeric")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProjectGoalPropertyNumeric extends ProjectGoalProperty {

    private double min;
    private double max;
}
