/*
 * Copyright 2015-2020 Peter Michael Haller and contributors
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

import de.hallerweb.enterprise.prioritize.model.Department;

/**
 * Interface to indicate that the implementing JPA entity is a protected resource. Access to it can be controlled by assigning a
 * {@link Role} with an adequate {@link PermissionRecord}. All relevant Objects in Prioritize which need to be protected implement this
 * interface.
 *
 * @author peter
 */

public interface PAuthorizedObject {

    public int getId();

    public Department getDepartment();
}
