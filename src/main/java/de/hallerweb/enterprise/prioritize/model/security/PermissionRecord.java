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

package de.hallerweb.enterprise.prioritize.model.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.hallerweb.enterprise.prioritize.model.PObject;
import de.hallerweb.enterprise.prioritize.model.company.Department;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionRecord extends PObject implements PAuthorizedObject {

    private boolean createPermission;
    private boolean readPermission;
    private boolean updatePermission;
    private boolean deletePermission;

    private String absoluteObjectType;
    private String objectName;
    private Long objectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private Department department;

    // This method is called automatically before every INSERT and UPDATE
    @jakarta.persistence.PrePersist
    @jakarta.persistence.PreUpdate
    public void updateObjectName() {
        if (absoluteObjectType != null && absoluteObjectType.contains(".")) {
            this.objectName = absoluteObjectType.substring(absoluteObjectType.lastIndexOf('.') + 1);
        } else {
            this.objectName = absoluteObjectType;
        }
    }
}