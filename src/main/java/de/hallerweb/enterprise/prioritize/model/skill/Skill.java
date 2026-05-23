package de.hallerweb.enterprise.prioritize.model.skill;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.security.PAuthorizedObject;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Builder
public class Skill extends PObject implements PAuthorizedObject {

    @ToString.Include
    private String name;

    @Column(length = 1024)
    private String description;

    private String keywords;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private SkillCategory category;

    @Builder.Default
    @OneToMany(mappedBy = "skill", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference(value = "skill-properties")
    private Set<SkillProperty> skillProperties = new HashSet<>();

    // Helper für bidirektionale Verknüpfung (Wichtig für JPA!)
    public void addSkillProperty(SkillProperty property) {
        skillProperties.add(property);
        property.setSkill(this);
    }

    public void removeSkillProperty(SkillProperty property) {
        skillProperties.remove(property);
        property.setSkill(null);
    }

    @Override
    public String toString() {
        return name;
    }
}