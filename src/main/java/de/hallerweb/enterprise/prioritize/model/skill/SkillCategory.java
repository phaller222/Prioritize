package de.hallerweb.enterprise.prioritize.model.skill;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
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
    @JsonBackReference(value = "category-tree")
    private SkillCategory parentCategory;

    @Builder.Default
    @OneToMany(mappedBy = "parentCategory", cascade = CascadeType.ALL)
    @JsonManagedReference(value = "category-tree")
    private Set<SkillCategory> subCategories = new HashSet<>();

    public String getQualifiedName() {
        if (parentCategory == null) {
            return name;
        }
        return parentCategory.getQualifiedName() + "-" + name;
    }
}

