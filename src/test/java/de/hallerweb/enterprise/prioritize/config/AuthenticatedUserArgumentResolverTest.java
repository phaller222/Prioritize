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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthenticatedUserArgumentResolver}: which parameters it claims, and that the
 * user is looked up <b>once per request</b>. Plain Mockito plus Spring's servlet mocks, no Spring
 * context.
 *
 * @author peter haller
 */
class AuthenticatedUserArgumentResolverTest {

    private CurrentUserResolver currentUserResolver;
    private AuthenticatedUserArgumentResolver resolver;
    private ServletWebRequest webRequest;

    private final PUser user = new PUser();
    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("admin", "p@ssword", List.of());

    /** Signature only — supplies the {@link MethodParameter}s under test. */
    @SuppressWarnings("unused")
    private void sample(@AuthenticatedUser PUser annotated, PUser plain, @AuthenticatedUser String wrongType) {
        // never invoked
    }

    private MethodParameter parameter(int index) throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod("sample", PUser.class, PUser.class, String.class);
        return new MethodParameter(method, index);
    }

    @BeforeEach
    void setUp() {
        currentUserResolver = mock(CurrentUserResolver.class);
        resolver = new AuthenticatedUserArgumentResolver(currentUserResolver);
        webRequest = new ServletWebRequest(new MockHttpServletRequest());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(currentUserResolver.resolve(any())).thenReturn(user);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("supportsParameter: only an @AuthenticatedUser PUser parameter is claimed")
    void supportsParameter() throws NoSuchMethodException {
        assertTrue(resolver.supportsParameter(parameter(0)));
        assertFalse(resolver.supportsParameter(parameter(1)), "PUser without the annotation");
        assertFalse(resolver.supportsParameter(parameter(2)), "annotation on the wrong type");
    }

    @Test
    @DisplayName("resolveArgument: delegates to CurrentUserResolver with the current authentication")
    void resolveArgument_delegates() throws Exception {
        assertSame(user, resolver.resolveArgument(parameter(0), null, webRequest, null));
        verify(currentUserResolver).resolve(authentication);
    }

    @Test
    @DisplayName("resolveArgument: a second parameter in the same request reuses the cached user")
    void resolveArgument_cachesPerRequest() throws Exception {
        PUser first = resolver.resolveArgument(parameter(0), null, webRequest, null);
        PUser second = resolver.resolveArgument(parameter(0), null, webRequest, null);

        assertSame(first, second);
        verify(currentUserResolver, times(1)).resolve(any());
    }

    @Test
    @DisplayName("resolveArgument: a new request looks the user up again")
    void resolveArgument_cacheIsPerRequest() throws Exception {
        resolver.resolveArgument(parameter(0), null, webRequest, null);
        resolver.resolveArgument(parameter(0), null, new ServletWebRequest(new MockHttpServletRequest()), null);

        verify(currentUserResolver, times(2)).resolve(any());
    }
}
