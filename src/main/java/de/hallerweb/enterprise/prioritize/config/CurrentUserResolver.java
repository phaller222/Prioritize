package de.hallerweb.enterprise.prioritize.config;

import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.service.security.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Zentrale Brücke zwischen Spring Security {@link Authentication} und der
 * Domänen-Entität {@link PUser}.
 * <p>
 * Im Dev-Modus (Basic Auth) liefert {@code auth.getName()} den Benutzernamen.
 * Für den späteren Keycloak-Modus (OAuth2 Resource Server) wird hier der
 * JWT-Zweig ergänzt, der den {@code preferred_username}-Claim auswertet.
 * <p>
 * Gemäß Projektkonvention ermitteln Controller den {@link PUser} über diesen
 * Resolver und übergeben ihn explizit an die Services. Die eigentliche
 * Berechtigungsprüfung bleibt im Service.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserService userService;

    public PUser resolve(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Kein Benutzer angemeldet.");
        }
        // Basic Auth: Principal ist ein UserDetails, getName() liefert den Username.
        // Keycloak (später): hier JwtAuthenticationToken behandeln und
        // preferred_username aus den Claims lesen.
        String username = auth.getName();
        return userService.findUserByUsername(username);
    }
}
