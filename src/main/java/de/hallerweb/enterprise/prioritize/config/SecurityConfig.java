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

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import de.hallerweb.enterprise.prioritize.ui.security.LoginView;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security for the non-Keycloak profile, split into two filter chains so the stateless REST API and
 * the session-based Vaadin admin GUI can coexist:
 * <ul>
 *   <li><b>REST chain</b> ({@link Order @Order(1)}) matches {@code /api/**} plus the OpenAPI/H2
 *       tooling and keeps HTTP Basic auth, exactly as before.</li>
 *   <li><b>Vaadin chain</b> ({@link Order @Order(2)}) is the catch-all for everything else and wires
 *       form-based login through the {@link VaadinSecurityConfigurer}, redirecting anonymous users to
 *       {@link LoginView}.</li>
 * </ul>
 * Both authenticate against the same local user store (the {@code UserService} {@code
 * UserDetailsService} + the BCrypt {@code PasswordEncoder} from {@code PasswordConfig}). With the
 * {@code keycloak} profile active this class is disabled in favor of {@code KeycloakSecurityConfig}.
 *
 * @author peter haller
 */
@Configuration
@EnableWebSecurity
@Profile("!keycloak")
public class SecurityConfig {

    /** REST API and tooling: stateless HTTP Basic, unchanged behavior. */
    @Bean
    @Order(1)
    public SecurityFilterChain restFilterChain(HttpSecurity http) throws Exception {
        // Basic challenge entry point for unauthenticated API calls, so REST clients get a 401 with a
        // WWW-Authenticate header rather than the GUI login form.
        // Note: we write the 401 directly via setStatus instead of the stock BasicAuthenticationEntryPoint
        // (which calls response.sendError). sendError triggers a servlet ERROR dispatch to /error, and
        // that dispatch does NOT match this chain's "/api/**" securityMatcher, so it falls through to the
        // Vaadin catch-all chain whose entry point redirects to /login (302) — masking the 401. Writing
        // the status directly commits the response without an error dispatch, keeping the clean 401.
        AuthenticationEntryPoint entryPoint = (request, response, authException) -> {
            response.setHeader("WWW-Authenticate", "Basic realm=\"Prioritize API\"");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        };

        http
                .securityMatcher(
                        "/api/**",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/h2-console/**"
                )
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/h2-console/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                .headers(headers -> headers.frameOptions(frame -> frame.disable())); // For the H2 console

        return http.build();
    }

    /** Vaadin admin GUI: form login via the Vaadin security integration. */
    @Bean
    @Order(2)
    public SecurityFilterChain vaadinFilterChain(HttpSecurity http) throws Exception {
        http.with(VaadinSecurityConfigurer.vaadin(), configurer -> configurer.loginView(LoginView.class));
        return http.build();
    }
}
