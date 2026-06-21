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

import com.fasterxml.jackson.annotation.JsonManagedReference;
import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.address.Address;
import de.hallerweb.enterprise.prioritize.model.security.PAuthorizedObject;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * JPA entity to represent a {@link Company}. A Company is the object at the
 * very top of the Prioritize hierarchie. A Company can have one or more
 * {@link Department} objects.
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
@ToString(onlyExplicitlyIncluded = true)
@Builder
public class Company extends PObject implements PAuthorizedObject {


    @ToString.Include
    @EqualsAndHashCode.Include
    private String name;
    private String description;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    private Address mainAddress;

    private String url;
    private String vatNumber;
    private String taxId;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY )
    @JsonManagedReference(value = "companyBackRef") // The "owner" of the relationship
    @Builder.Default
    private Set<Department> departments = new HashSet<>();

    @EqualsAndHashCode.Include
    @Override
    public Long getId() {
        return super.getId();
    }

    // Synchronisations-Hilfsmethode
    public void addDepartment(Department dept) {
        if (dept == null) return;
        if (this.departments.add(dept)) {
            dept.setCompany(this);
        }
    }

    public void removeDepartment(Department dept) {
        if (dept == null) return;
        if (this.departments.remove(dept)) {
            dept.setCompany(null);
        }
    }
}