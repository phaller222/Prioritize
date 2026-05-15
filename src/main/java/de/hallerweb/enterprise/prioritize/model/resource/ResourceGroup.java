package de.hallerweb.enterprise.prioritize.model.resource;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.company.Department;
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
@ToString(onlyExplicitlyIncluded = true)
public class ResourceGroup extends PObject implements PAuthorizedObject {

    public static final String DEFAULT_GROUP_NAME = "Default";

    @ToString.Include
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    @JsonBackReference(value = "resourceGroupDeptRef")
    private Department department;

    @Builder.Default
    @OneToMany(mappedBy = "resourceGroup", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference(value = "resourceGroupResources")
    private Set<Resource> resources = new HashSet<>();

    @EqualsAndHashCode.Include
    @Override
    public Integer getId() {
        return super.getId();
    }

    // Hilfsmethode für bidirektionale Konsistenz
    public void addResource(Resource resource) {
        if (resources == null) {
            resources = new HashSet<>();
        }
        resources.add(resource);
        resource.setResourceGroup(this);
    }
}