package de.hallerweb.enterprise.prioritize.model.skill;

import de.hallerweb.enterprise.prioritize.model.PObject;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class SkillGroup extends PObject {

    @ManyToOne
    private Skill skill;
    private int amount;
}