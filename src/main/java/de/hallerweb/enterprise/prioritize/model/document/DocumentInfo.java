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
import com.fasterxml.jackson.annotation.JsonManagedReference;
import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.security.PAuthorizedObject;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * JPA entity to represent a reference to a {@link Document} and it's history.
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
@EqualsAndHashCode(callSuper = true)
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class DocumentInfo extends PObject implements PAuthorizedObject {

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "current_document_id")
    private Document currentDocument;


    @JsonBackReference(value = "documentGroupBackRef")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_group_id")
    private DocumentGroup documentGroup;

    @Builder.Default
    @OneToMany(mappedBy = "documentInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("version DESC") // Neueste Versionen zuerst ist meist sinnvoller
    @JsonManagedReference
    private Set<Document> recentDocuments = new HashSet<>();

    @ToString.Include
    private boolean locked;

    @ManyToOne
    @ToString.Include
    private PUser lockedBy;
}
