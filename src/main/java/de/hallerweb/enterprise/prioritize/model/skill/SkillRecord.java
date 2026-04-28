package de.hallerweb.enterprise.prioritize.model.skill;


import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.resource.Resource;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
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
public class SkillRecord extends PObject {

    @ManyToOne(fetch = FetchType.LAZY)
    private Skill skill;

    @ManyToOne(fetch = FetchType.LAZY)
    private PUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    private Resource resource;

    @Builder.Default
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "skill_record_id")
    private Set<SkillRecordProperty> skillRecordProperties = new HashSet<>();

    private int enthusiasm; // 0 bis 10

    // Helper für Properties
    public void addSkillRecordProperty(SkillRecordProperty prop) {
        this.skillRecordProperties.add(prop);
    }
}
