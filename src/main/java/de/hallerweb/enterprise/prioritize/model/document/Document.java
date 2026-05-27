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

package de.hallerweb.enterprise.prioritize.model.document;

import com.fasterxml.jackson.annotation.JsonBackReference;
import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.security.PUser;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

/**
 * JPA entity to represent a document.
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
public class Document extends PObject {

    public static final String PROPERTY_NAME = "name";
    public static final String PROPERTY_MIMETYPE = "mimeType";
    public static final String PROPERTY_TAG = "tag";
    public static final String PROPERTY_ENCRYPTED = "encrypted";
    public static final String PROPERTY_CHANGES = "changes";

    @EqualsAndHashCode.Include // We include the ID, even if it is null.
    @Override
    public Long getId() {
        return super.getId();
    }

    @ToString.Include
    private String name; // Name of the document.
    @ToString.Include
    private int version; // Version of the document.
    @ToString.Include
    private String mimeType; // MIME type.
    @ToString.Include
    private String tag; // Tag for this document, if it has been tagged.
    @LastModifiedDate
    @ToString.Include
    private LocalDateTime lastModified;  // Date of the last modification.
    @ToString.Include
    private String changes; // Description of the last changes made.

    @LastModifiedBy
    @ManyToOne
    private PUser lastModifiedBy; // User who last modified this document.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_info_id")
    @JsonBackReference
    private DocumentInfo documentInfo;

    @Column(name = "data", columnDefinition = "bytea")
    private byte[] data; // Document data, for example binary MS Word data.
}
