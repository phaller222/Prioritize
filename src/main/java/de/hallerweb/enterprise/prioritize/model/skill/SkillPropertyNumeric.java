package de.hallerweb.enterprise.prioritize.model.skill;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.*;

@Entity
@DiscriminatorValue("NUMERIC")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor // Für Builder
@Builder
public class SkillPropertyNumeric extends SkillProperty {
    private int minimum;
    private int maximum;
}