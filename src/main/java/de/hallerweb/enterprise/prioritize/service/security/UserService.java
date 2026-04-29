package de.hallerweb.enterprise.prioritize.service.security;

import de.hallerweb.enterprise.prioritize.model.security.PUser;
import de.hallerweb.enterprise.prioritize.repository.security.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Ermittelt den aktuell angemeldeten Benutzer aus dem Spring Security Context.
     */
    public PUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Kein Benutzer angemeldet.");
        }
        String currentPrincipalName = authentication.getName();
        return userRepository.findByUsername(currentPrincipalName)
                .orElseThrow(() -> new NoSuchElementException("Benutzer nicht gefunden."));
    }

    public List<PUser> getAllUsers() {
        return userRepository.findAll();
    }

    public PUser createUser(PUser user) {
        // Passwort verschlüsseln, bevor es in die DB geht
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Sucht den PUser in deiner Datenbank
        PUser pUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User nicht gefunden: " + username));

        // Changes PUser to a Spring-Security-UserDetails Object
        return org.springframework.security.core.userdetails.User.builder()
                .username(pUser.getUsername())
                .password(pUser.getPassword()) // Das Passwort aus der DB (BCrypt-Encoded)
                .authorities(pUser.isAdmin() ? "ROLE_ADMIN" : "ROLE_USER")
                .build();
    }

}