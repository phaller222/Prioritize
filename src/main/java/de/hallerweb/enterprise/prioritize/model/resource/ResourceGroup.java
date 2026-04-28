package de.hallerweb.enterprise.prioritize.model.resource;

import com.fasterxml.jackson.annotation.JsonBackReference;
import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.*;


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class ResourceGroup extends PObject {

    @ToString.Include
    String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    @JsonBackReference(value = "resourceGroupDeptRef")
    private Department department;

}
