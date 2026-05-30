/*
 * Copyright 2015-2024 Peter Michael Haller and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.hallerweb.enterprise.prioritize.model.company;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.address.Address;
import de.hallerweb.enterprise.prioritize.model.document.DocumentGroup;
import de.hallerweb.enterprise.prioritize.model.resource.ResourceGroup;
import de.hallerweb.enterprise.prioritize.model.security.PAuthorizedObject;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * JPA entity to represent a {@link Department} of a {@link Company}. A Department has a key role in the Prioritize authorization mechanism.
 * All objects which implement PAuthorizedObject must provide a Department. Usually this is the department the object belongs to (e.G. User
 * X works for Department Y). If the Department is null for any {@link PAuthorizedObject} this is handled as a special case.
 *
 * <p>
 * Copyright: (c) 2014
 * </p>
 * <p>
 * Peter Haller
 * </p>
 *
 * @author peter
 */

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class Department extends PObject implements PAuthorizedObject {

    @JsonIgnore
    String token; // Secret token for things to be placed in this department

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "address_id")
    @ToString.Include
    Address address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    @JsonBackReference(value = "companyBackRef")
    private Company company;

    @Builder.Default
    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference(value = "documentGroupDeptRef")
    private Set<DocumentGroup> documentGroups = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference(value = "resourceGroupDeptRef")
    private Set<ResourceGroup> resourceGroups = new HashSet<>();

    @ToString.Include
    @EqualsAndHashCode.Include
    String name;
    @ToString.Include
    String description;

    @EqualsAndHashCode.Include
    @Override
    public Long getId() {
        return super.getId();
    }

    public void addDocumentGroup(DocumentGroup documentGroup) {
        if (documentGroup == null) return;

        if (this.documentGroups.add(documentGroup)) {
            documentGroup.setDepartment(this);
        }
    }

    public void removeDocumentGroup(DocumentGroup documentGroup) {
        if (documentGroup == null) {
            return;
        }
        if (this.documentGroups.remove(documentGroup)) {
            documentGroup.setDepartment(null);
        }
    }

    public void addResourceGroup(ResourceGroup resourceGroup) {
        if (resourceGroup == null) {
            return;
        }

        if (this.resourceGroups.add(resourceGroup)) {
            resourceGroup.setDepartment(this);
        }
    }

    public void removeResourceGroup(ResourceGroup resourceGroup) {
        if (resourceGroup == null) {
            return;
        }
        if (this.resourceGroups.remove(resourceGroup)) {
            resourceGroup.setDepartment(null);
        }
    }
}
