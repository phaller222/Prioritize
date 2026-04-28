package de.hallerweb.enterprise.prioritize.config;

import de.hallerweb.enterprise.prioritize.model.security.PUser;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AuditorAwareImpl implements AuditorAware<PUser> {
    @Override
    public Optional<PUser> getCurrentAuditor() {
        // Holt den aktuell eingeloggten User aus dem Security-Context
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(auth -> auth.isAuthenticated())
                .map(auth -> (PUser) auth.getPrincipal());
    }
}