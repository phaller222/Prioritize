package de.hallerweb.enterprise.prioritize.model.skill;

import de.hallerweb.enterprise.prioritize.model.PObject;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class SkillRecordProperty extends PObject {

    @ManyToOne(fetch = FetchType.LAZY)
    private SkillProperty property;

    private int valueNumeric;
    private String valueText;
}