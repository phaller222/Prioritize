package de.hallerweb.enterprise.prioritize.repository.security;

import de.hallerweb.enterprise.prioritize.model.security.PUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<PUser, Integer> {
    Optional<PUser> findByUsername(String username);
}