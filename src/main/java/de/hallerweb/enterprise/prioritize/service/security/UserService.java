package de.hallerweb.enterprise.prioritize.service.security;

import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.security.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Ermittelt den aktuell angemeldeten Benutzer aus dem Spring Security Context.
     */
    public PUser getCurrentUser() {
        // Wir nehmen an, dass der "Principal" im SecurityContext der Benutzername (String) ist
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Aktueller Benutzer nicht in der Datenbank gefunden."));
    }
}