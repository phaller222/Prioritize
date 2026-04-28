package de.hallerweb.enterprise.prioritize.model.skill;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.*;

@Entity
@DiscriminatorValue("TEXT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor // Für Builder
@Builder
public class SkillPropertyText extends SkillProperty {
    @Column(length = 2048)
    private String textValue;
}