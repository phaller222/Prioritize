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

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document metadata for the published REST API.
 * <p>
 * Built programmatically rather than through annotations so the {@code info.version} tracks the
 * application version from the build ({@code @project.version@} filtered into {@code application.yaml})
 * instead of being a second place to bump on every release.
 * <p>
 * Two authentication schemes are declared because the app runs one of two mutually exclusive security
 * chains depending on the active profile (see {@code SecurityConfig} / {@code KeycloakSecurityConfig}):
 * <b>{@code basicAuth}</b> in the default (dev) profiles and <b>{@code bearerAuth}</b> (a Keycloak-issued
 * JWT) under the {@code keycloak} profile. Both are offered as alternatives on every operation; a client
 * uses whichever matches the deployment it talks to.
 *
 * @author peter haller
 */
@Configuration
public class OpenApiConfig {

    private final String applicationVersion;

    public OpenApiConfig(@Value("${prioritize.version:0.0.0-UNKNOWN}") String applicationVersion) {
        this.applicationVersion = applicationVersion;
    }

    @Bean
    public OpenAPI prioritizeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Prioritize API")
                        .version(applicationVersion)
                        .description("""
                                REST API of Prioritize, a resource, project and IoT orchestration platform. \
                                All endpoints live under /api/v1. From 1.0.0 on this REST surface is the \
                                stable public contract (SemVer); the Vaadin admin GUI and its @Route URLs are \
                                an implementation detail and NOT part of that promise. A few areas are marked \
                                'experimental' in their schema and may still change within the 1.x line.""")
                        .license(new License()
                                .name("Apache License 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0"))
                        .contact(new Contact()
                                .name("Peter Haller")
                                .email("peter.haller@prioritize-iot.de")
                                .url("https://github.com/phaller222/Prioritize")))
                .components(new Components()
                        .addSecuritySchemes("basicAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("HTTP Basic authentication against the local user store "
                                        + "(default/dev profiles)."))
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Keycloak-issued JWT bearer token (keycloak profile).")))
                // Offered as alternatives: a deployment runs exactly one of the two chains.
                .addSecurityItem(new SecurityRequirement().addList("basicAuth").addList("bearerAuth"));
    }

    /**
     * Gives every operation a <b>stable, unique</b> {@code operationId} derived deterministically from
     * its controller and method: {@code <resource>_<method>}, e.g. {@code company_create} or
     * {@code projectInstance_startForProject}. This replaces springdoc's default (the bare method name,
     * with {@code _1}/{@code _2} suffixes appended in registration order whenever names collide across
     * controllers — and {@code create}/{@code update}/{@code delete}/{@code getById} collide several
     * times here). Those default ids are order-dependent and therefore unfit to freeze: a generated
     * client's method names would shift when an unrelated endpoint is added. The scheme here is
     * self-maintaining — a new endpoint gets a stable id with no annotation to remember — and the
     * {@code <resource>_} prefix makes the ids unique across controllers while method names stay unique
     * within one. Part of the 1.0 API-stability promise; see the class Javadoc.
     */
    @Bean
    public OperationCustomizer stableOperationIds() {
        return (operation, handlerMethod) -> {
            String controller = handlerMethod.getBeanType().getSimpleName();
            String resource = controller.replaceAll("(Rest)?Controller$", "");
            if (!resource.isEmpty()) {
                resource = Character.toLowerCase(resource.charAt(0)) + resource.substring(1);
            }
            operation.setOperationId(resource + "_" + handlerMethod.getMethod().getName());
            return operation;
        };
    }
}
