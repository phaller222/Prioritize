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
import org.springframework.transaction.annotation.Transactional;

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
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Kein Benutzer angemeldet.");
        }
        String currentPrincipalName = authentication.getName();
        PUser user = userRepository.findByUsername(currentPrincipalName).orElseThrow(() ->
                new NoSuchElementException("Benutzer nicht gefunden."));
        if (!user.isActive()) {
            throw new AccessDeniedException("Benutzer ist deaktiviert.");
        }
        return user;
    }

    public List<PUser> getAllUsers() {
        return userRepository.findAll().stream()
                .filter(PUser::isActive)
                .toList();
    }

    public PUser createUser(PUser user) {
        // Passwort verschlüsseln, bevor es in die DB geht
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }


    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Sucht den PUser in deiner Datenbank
        PUser pUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User nicht gefunden: " + username));

        // Changes PUser to a Spring-Security-UserDetails Object
        return org.springframework.security.core.userdetails.User.builder()
                .username(pUser.getUsername())
                .password(pUser.getPassword()) // Das Passwort aus der DB (BCrypt-Encoded)
                .authorities(pUser.isAdmin() ? "ROLE_ADMIN" : "ROLE_USER")
                .disabled(!pUser.isActive()) // Inaktive User werden von Spring Security blockiert
                .build();
    }


    public PUser findUserByUsername(String username) {
        PUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("User " + username + " nicht gefunden"));
        if (!user.isActive()) {
            throw new NoSuchElementException("User " + username + " nicht gefunden");
        }
        return user;
    }

    @Transactional
    public PUser updateUser(PUser user) {
        if (!userRepository.existsById(user.getId())) {
            throw new NoSuchElementException("User mit ID " + user.getId() + " existiert nicht.");
        }
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public PUser getUserById(Long id) {
        PUser user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User mit ID " + id + " nicht gefunden"));
        if (!user.isActive()) {
            throw new NoSuchElementException("User mit ID " + id + " nicht gefunden");
        }
        return user;
    }

    @Transactional
    public PUser partialUpdateUser(Long id, PUser patch) {
        PUser existing = getUserById(id);

        // Nur unkritische Felder per PATCH änderbar
        if (patch.getName() != null) existing.setName(patch.getName());
        if (patch.getFirstname() != null) existing.setFirstname(patch.getFirstname());
        if (patch.getEmail() != null) existing.setEmail(patch.getEmail());
        if (patch.getOccupation() != null) existing.setOccupation(patch.getOccupation());
        if (patch.getGender() != null) existing.setGender(patch.getGender());
        if (patch.getDateOfBirth() != null) existing.setDateOfBirth(patch.getDateOfBirth());
        if (patch.getAddress() != null) existing.setAddress(patch.getAddress());

        // Passwort nur ändern wenn explizit mitgeschickt – und dann verschlüsseln
        if (patch.getPassword() != null && !patch.getPassword().isBlank()) {
            existing.setPassword(passwordEncoder.encode(patch.getPassword()));
        }

        // Rollen, Berechtigungen und admin-Flag niemals per PATCH änderbar!
        // Dafür braucht es dedizierte Endpoints mit erhöhter Autorisierung.

        return userRepository.save(existing);
    }

    @Transactional
    public void deactivateUser(Long id) {
        PUser user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User mit ID " + id + " nicht gefunden"));
        if (user.isAdmin()) {
            throw new IllegalArgumentException("Admin-User können nicht deaktiviert werden.");
        }
        user.setActive(false);
    }
}