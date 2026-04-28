package de.hallerweb.enterprise.prioritize.model.skill;

import de.hallerweb.enterprise.prioritize.model.PObject;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class SkillCategory extends PObject { // Interface PAuthorizedObject entfernt


    @ToString.Include
    private String name;
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id")
    private SkillCategory parentCategory;

    @Builder.Default
    @OneToMany(mappedBy = "parentCategory", cascade = CascadeType.ALL)
    private Set<SkillCategory> subCategories = new HashSet<>();

    public String getQualifiedName() {
        if (parentCategory == null) {
            return name;
        }
        return parentCategory.getQualifiedName() + "-" + name;
    }
}

