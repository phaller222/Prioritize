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

package de.hallerweb.enterprise.prioritize.config;

import de.hallerweb.enterprise.prioritize.model.security.PUser;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method parameter as the {@link PUser} behind the current request. The parameter
 * is filled by {@link AuthenticatedUserArgumentResolver}, which resolves the user <b>once per
 * request</b> — controllers no longer take an {@code Authentication} and no longer carry their own
 * {@code getCurrentUser(auth)} helper.
 * <p>
 * The parameter is never bound from the request itself; it is excluded from the OpenAPI document in
 * {@link OpenApiConfig}, so a {@code PUser} parameter stays invisible to API clients.
 *
 * @author peter haller
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthenticatedUser {
}
