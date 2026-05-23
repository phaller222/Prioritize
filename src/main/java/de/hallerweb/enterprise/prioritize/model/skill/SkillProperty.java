package de.hallerweb.enterprise.prioritize.model.skill;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "property_type")
@Getter
@Setter
@NoArgsConstructor // Für JPA
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,          // Wir nutzen logische Namen zur Unterscheidung
        include = JsonTypeInfo.As.PROPERTY,  // Der Typ-Indikator wird als Feld ins JSON gepackt
        property = "type"                    // Das JSON-Feld heißt "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SkillPropertyNumeric.class, name = "NUMERIC"), // Verknüpft den Namen "NUMERIC" mit der Klasse
        @JsonSubTypes.Type(value = SkillPropertyText.class, name = "TEXT") // Verknüpft den Namen "TEXT" mit der Klasse
})
public abstract class SkillProperty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;

    @ToString.Include
    private String name;
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id")
    @JsonBackReference(value = "skill-properties")
    private Skill skill;
}