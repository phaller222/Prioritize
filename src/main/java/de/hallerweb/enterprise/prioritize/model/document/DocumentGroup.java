/*
 * Copyright 2026 Peter Michael Haller and contributors
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

package de.hallerweb.enterprise.prioritize.model.document;

import com.fasterxml.jackson.annotation.JsonBackReference;
import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import de.hallerweb.enterprise.prioritize.model.security.PAuthorizedObject;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * JPA entity to represent a Directory of documents within a department.
 *
 * @author peter
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class DocumentGroup extends PObject implements PAuthorizedObject {

    public static final String DEFAULT_GROUP_NAME = "Default";

    @ToString.Include
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    @JsonBackReference(value = "documentGroupDeptRef")
    private Department department;



    @Builder.Default
    @OneToMany(mappedBy = "documentGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonBackReference(value = "documentsBackRef")
    @EqualsAndHashCode.Exclude
    private Set<DocumentInfo> documents = new HashSet<>();

    public void addDocument(DocumentInfo info) {
        if (info == null) return;
        if (this.documents.add(info)) {
            info.setDocumentGroup(this);
        }
    }


    public void removeDocument(DocumentInfo info) {
        if (this.documents.remove(info)) {
            info.setDocumentGroup(null);
        }
    }

}
