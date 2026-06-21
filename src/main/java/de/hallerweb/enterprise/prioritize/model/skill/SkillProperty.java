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
@NoArgsConstructor // For JPA
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,          // We use logical names for differentiation
        include = JsonTypeInfo.As.PROPERTY,  // The type indicator is embedded as a field in the JSON
        property = "type"                    // The JSON field is called "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SkillPropertyNumeric.class, name = "NUMERIC"), // Associates the name "NUMERIC" with the class
        @JsonSubTypes.Type(value = SkillPropertyText.class, name = "TEXT") // Associates the name "TEXT" with the class
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